/*
 * Copyright (C) 2015 HaiYang Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.landawn.abacus.util;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TupleValue;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;
import com.datastax.driver.core.exceptions.InvalidTypeException;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.landawn.abacus.DataSet;
import com.landawn.abacus.DirtyMarker;
import com.landawn.abacus.condition.And;
import com.landawn.abacus.condition.Condition;
import com.landawn.abacus.condition.ConditionFactory.L;
import com.landawn.abacus.exception.NonUniqueResultException;
import com.landawn.abacus.pool.KeyedObjectPool;
import com.landawn.abacus.pool.PoolFactory;
import com.landawn.abacus.pool.PoolableWrapper;
import com.landawn.abacus.type.Type;
import com.landawn.abacus.util.CQLBuilder.CP;
import com.landawn.abacus.util.CQLBuilder.NE;
import com.landawn.abacus.util.CQLBuilder.NE2;
import com.landawn.abacus.util.CQLBuilder.NE3;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.stream.Stream;

/**
 * It's a simple wrapper of Cassandra Java client.
 * Raw/parameterized cql are supported. The parameters can be array/list/map/entity:
 * <li> row parameterized cql with question mark: <code>SELECT * FROM account WHERE id = ?</li>
 * <li> Parameterized cql with named parameter: <code>SELECT * FROM account WHERE id = :id</li>
 * 
 * <br />
 * <code>CQLBuilder</code> is designed to build CQL.
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 * 
 * @see CQLBuilder
 */
public final class CassandraExecutor implements Closeable {

    static final String ID = "id";
    static final ImmutableSet<String> ID_SET = ImmutableSet.of(ID);
    static final List<String> EXISTS_SELECT_PROP_NAMES = ImmutableList.of("1");
    static final List<String> COUNT_SELECT_PROP_NAMES = ImmutableList.of(NE.COUNT_ALL);

    static final int POOLABLE_LENGTH = 1024;

    private static final Map<String, Class<?>> namedDataType = new HashMap<String, Class<?>>();

    static {
        namedDataType.put("BOOLEAN", Boolean.class);
        namedDataType.put("CHAR", Character.class);
        namedDataType.put("Character", Character.class);
        namedDataType.put("TINYINT", Byte.class);
        namedDataType.put("SMALLINT", Short.class);
        namedDataType.put("INT", Integer.class);
        namedDataType.put("BIGINT", Long.class);
        namedDataType.put("FLOAT", Float.class);
        namedDataType.put("DOUBLE", Double.class);
        namedDataType.put("BIGINT", Long.class);
        namedDataType.put("VARINT", BigInteger.class);
        namedDataType.put("DECIMAL", BigDecimal.class);
        namedDataType.put("TEXT", String.class);
        namedDataType.put("ASCII", String.class);
        namedDataType.put("INET", InetAddress.class);
        namedDataType.put("TIME", Long.class);

        try {
            namedDataType.put("DATE", ClassUtil.forClass("com.datastax.driver.core.LocalDate"));
        } catch (Exception e) {
            // ignore
        }

        namedDataType.put("TIMESTAMP", Date.class);
        namedDataType.put("VARCHAR", String.class);
        namedDataType.put("BLOB", ByteBuffer.class);
        namedDataType.put("COUNTER", Long.class);
        namedDataType.put("UUID", UUID.class);
        namedDataType.put("TIMEUUID", UUID.class);
        namedDataType.put("LIST", List.class);
        namedDataType.put("SET", Set.class);
        namedDataType.put("MAP", Map.class);
        namedDataType.put("UDT", UDTValue.class);
        namedDataType.put("TUPLE", TupleValue.class);
        namedDataType.put("CUSTOM", ByteBuffer.class);
    }

    private static final Map<Class<?>, Set<String>> entityKeyNamesMap = new ConcurrentHashMap<>();

    private final KeyedObjectPool<String, PoolableWrapper<Statement>> stmtPool = PoolFactory.createKeyedObjectPool(1024, 3000);
    private final KeyedObjectPool<String, PoolableWrapper<PreparedStatement>> preStmtPool = PoolFactory.createKeyedObjectPool(1024, 3000);

    private final CQLMapper cqlMapper;

    private final Cluster cluster;
    private final Session session;
    private final MappingManager mappingManager;
    private final StatementSettings settings;
    private final NamingPolicy namingPolicy;
    private final AsyncExecutor asyncExecutor;

    public CassandraExecutor(final Session session) {
        this(session, null);
    }

    public CassandraExecutor(final Session session, final StatementSettings settings) {
        this(session, settings, null);
    }

    public CassandraExecutor(final Session session, final StatementSettings settings, final CQLMapper cqlMapper) {
        this(session, settings, cqlMapper, null);
    }

    public CassandraExecutor(final Session session, final StatementSettings settings, final CQLMapper cqlMapper, final NamingPolicy namingPolicy) {
        this(session, settings, cqlMapper, namingPolicy, null);
    }

    public CassandraExecutor(final Session session, final StatementSettings settings, final CQLMapper cqlMapper, final NamingPolicy namingPolicy,
            final AsyncExecutor asyncExecutor) {
        this.cluster = session.getCluster();
        this.session = session;
        this.mappingManager = new MappingManager(session);

        if (settings == null) {
            this.settings = null;
        } else {
            this.settings = settings.copy();
        }

        this.cqlMapper = cqlMapper;
        this.namingPolicy = namingPolicy == null ? NamingPolicy.LOWER_CASE_WITH_UNDERSCORE : namingPolicy;
        this.asyncExecutor = asyncExecutor == null ? new AsyncExecutor(64, 300, TimeUnit.SECONDS) : asyncExecutor;
    }

    AsyncExecutor asyncExecutor() {
        return asyncExecutor;
    }

    public Cluster cluster() {
        return cluster;
    }

    public Session session() {
        return session;
    }

    public <T> Mapper<T> mapper(Class<T> targetClass) {
        return mappingManager.mapper(targetClass);
    }

    public static void registerKeys(Class<?> entityClass, Collection<String> keyNames) {
        N.checkArgument(N.notNullOrEmpty(keyNames), "'keyNames' can't be null or empty");

        final Set<String> keyNameSet = new LinkedHashSet<>(N.initHashCapacity(keyNames.size()));

        for (String keyName : keyNames) {
            keyNameSet.add(ClassUtil.getPropNameByMethod(ClassUtil.getPropGetMethod(entityClass, keyName)));
        }

        entityKeyNamesMap.put(entityClass, ImmutableSet.of(keyNameSet));
    }

    public static DataSet extractData(final ResultSet resultSet) {
        return extractData(Map.class, resultSet);
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method or <code>Map.class</code>
     * @param resultSet
     * @return
     */
    public static DataSet extractData(final Class<?> targetClass, final ResultSet resultSet) {
        checkTargetClass(targetClass);

        final ColumnDefinitions columnDefinitions = resultSet.getColumnDefinitions();
        final int columnCount = columnDefinitions.size();
        final List<Row> rowList = resultSet.all();
        final int rowCount = N.isNullOrEmpty(rowList) ? 0 : rowList.size();

        final List<String> columnNameList = new ArrayList<>(columnCount);

        for (int i = 0; i < columnCount; i++) {
            columnNameList.add(columnDefinitions.getName(i));
        }

        final List<Object> entityList = new ArrayList<>(rowCount);

        for (Row row : rowList) {
            entityList.add(toEntity(targetClass, row));
        }

        return N.newDataSet(columnNameList, entityList);
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method, <code>Map.class</code> or basic single value type(Primitive/String/Date...)
     * @param resultSet
     * @return
     */
    public static <T> List<T> toList(final Class<T> targetClass, final ResultSet resultSet) {
        if (targetClass.isAssignableFrom(Row.class)) {
            return (List<T>) resultSet.all();
        }

        final Type<T> type = N.typeOf(targetClass);
        final ColumnDefinitions columnDefinitions = resultSet.getColumnDefinitions();
        final List<Row> rowList = resultSet.all();
        final List<Object> resultList = new ArrayList<>();

        if (type.isEntity() || type.isMap()) {
            for (Row row : rowList) {
                resultList.add(toEntity(targetClass, row, columnDefinitions));
            }
        } else if (columnDefinitions.size() == 1) {
            if (rowList.size() > 0) {
                if (rowList.get(0).getObject(0) != null && targetClass.isAssignableFrom(rowList.get(0).getObject(0).getClass())) {
                    for (Row row : rowList) {
                        resultList.add(row.getObject(0));
                    }
                } else {
                    for (Row row : rowList) {
                        resultList.add(N.as(targetClass, row.getObject(0)));
                    }
                }
            }
        } else {
            throw new IllegalArgumentException(
                    "Can't covert result with columns: " + columnDefinitions.toString() + " to class: " + ClassUtil.getCanonicalClassName(targetClass));
        }

        return (List<T>) resultList;
    }

    public static <T> T toEntity(final Class<T> targetClass, final ResultSet resultSet) {
        final Row row = resultSet.one();

        return row == null ? null : toEntity(targetClass, row);
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method or <code>Map.class</code>
     * @param row
     * @return
     */
    public static <T> T toEntity(final Class<T> targetClass, final Row row) {
        checkTargetClass(targetClass);

        if (row == null) {
            return null;
        }

        return toEntity(targetClass, row, row.getColumnDefinitions());
    }

    static <T> T toEntity(final Class<T> targetClass, final Row row, final ColumnDefinitions columnDefinitions) {
        final int columnCount = columnDefinitions.size();

        if (Map.class.isAssignableFrom(targetClass)) {
            final Map<String, Object> map = (Map<String, Object>) N.newInstance(targetClass);

            String propName = null;
            Object propValue = null;

            for (int i = 0; i < columnCount; i++) {
                propName = columnDefinitions.getName(i);
                propValue = row.getObject(i);

                if (propValue instanceof Row) {
                    map.put(propName, toEntity(targetClass, (Row) propValue));
                } else {
                    map.put(propName, propValue);
                }
            }

            return (T) map;
        } else {
            final T entity = N.newInstance(targetClass);

            String propName = null;
            Object propValue = null;
            Method propSetMethod = null;
            Class<?> parameterType = null;

            for (int i = 0; i < columnCount; i++) {
                propName = columnDefinitions.getName(i);
                propSetMethod = ClassUtil.getPropSetMethod(targetClass, propName);

                if (propSetMethod == null) {
                    if (propName.indexOf(D._PERIOD) > 0) {
                        ClassUtil.setPropValue(entity, propName, row.getObject(i), true);
                    }

                    continue;
                }

                parameterType = propSetMethod.getParameterTypes()[0];
                propValue = row.getObject(i);

                if (propValue == null || parameterType.isAssignableFrom(propValue.getClass())) {
                    ClassUtil.setPropValue(entity, propSetMethod, propValue);
                } else {
                    if (propValue instanceof Row) {
                        if (parameterType.isAssignableFrom(Map.class) || N.isEntity(parameterType)) {
                            ClassUtil.setPropValue(entity, propSetMethod, toEntity(parameterType, (Row) propValue));
                        } else {
                            ClassUtil.setPropValue(entity, propSetMethod, N.valueOf(parameterType, N.stringOf(toEntity(Map.class, (Row) propValue))));
                        }
                    } else {
                        ClassUtil.setPropValue(entity, propSetMethod, propValue);
                    }
                }
            }

            if (N.isDirtyMarker(entity.getClass())) {
                ((DirtyMarker) entity).markDirty(false);
            }

            return entity;
        }
    }

    static Condition ids2Cond(final Class<?> targetClass, final Object... ids) {
        N.checkNullOrEmpty(ids, "ids");

        final Set<String> keyNameSet = entityKeyNamesMap.get(targetClass);

        if (keyNameSet == null && ids.length == 1) {
            return L.eq(ID, ids[0]);
        } else if (keyNameSet != null && ids.length <= keyNameSet.size()) {
            final Iterator<String> iter = keyNameSet.iterator();
            final And and = new And();

            for (Object id : ids) {
                and.add(L.eq(iter.next(), id));
            }

            return and;
        } else {
            throw new IllegalArgumentException("The number: " + ids.length + " of input ids doesn't match the (registered) key names: "
                    + (keyNameSet == null ? "[id]" : N.toString(keyNameSet)) + " in class: " + ClassUtil.getCanonicalClassName(targetClass));
        }
    }

    static Condition entity2Cond(final Object entity) {
        final Class<?> targetClass = entity.getClass();
        final Set<String> keyNameSet = entityKeyNamesMap.get(targetClass);

        if (keyNameSet == null) {
            return L.eq(ID, ClassUtil.getPropValue(entity, ID));
        } else {
            final And and = new And();
            Object propVal = null;

            for (String keyName : keyNameSet) {
                propVal = ClassUtil.getPropValue(entity, keyName);

                if (propVal == null || (propVal instanceof CharSequence) && N.isNullOrEmpty(((CharSequence) propVal))) {
                    break;
                }

                and.add(L.eq(keyName, ClassUtil.getPropValue(entity, keyName)));
            }

            if (N.isNullOrEmpty(and.getConditions())) {
                throw new IllegalArgumentException("No property value specified in entity for key names: " + keyNameSet);
            }

            return and;
        }
    }

    @SafeVarargs
    public final <T> T get(final Class<T> targetClass, final Object... ids) throws NonUniqueResultException {
        return get(targetClass, null, ids);
    }

    @SafeVarargs
    public final <T> T get(final Class<T> targetClass, final Collection<String> selectPropNames, final Object... ids) throws NonUniqueResultException {
        return get(targetClass, selectPropNames, ids2Cond(targetClass, ids));
    }

    public <T> T get(final Class<T> targetClass, final Condition whereCause) throws NonUniqueResultException {
        return get(targetClass, null, whereCause);
    }

    /**
     * 
     * @param targetClass
     * @param selectPropNames
     * @param whereCause
     * @return
     * @throws NonUniqueResultException if more than one record found.
     */
    public <T> T get(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause) throws NonUniqueResultException {
        final CP pair = prepareQuery(targetClass, selectPropNames, whereCause, 2);
        final ResultSet resultSet = execute(pair.cql, pair.parameters.toArray());
        final Row row = resultSet.one();

        if (row == null) {
            return null;
        } else if (resultSet.isExhausted()) {
            return toEntity(targetClass, row);
        } else {
            throw new NonUniqueResultException();
        }
    }

    public ResultSet insert(final Object entity) {
        return insert(entity.getClass(), Maps.entity2Map(entity));
    }

    public ResultSet insert(final Class<?> targetClass, final Map<String, Object> props) {
        final CP pair = prepareAdd(targetClass, props);

        return this.execute(pair.cql, pair.parameters.toArray());
    }

    public ResultSet batchInsert(final Collection<?> entities, final BatchStatement.Type type) {
        N.checkArgument(N.notNullOrEmpty(entities), "'entities' can't be null or empty.");

        return batchInsert(entities.iterator().next().getClass(), Maps.entity2Map(entities), type);
    }

    public ResultSet batchInsert(final Class<?> targetClass, final Collection<? extends Map<String, Object>> propsList, final BatchStatement.Type type) {
        N.checkArgument(N.notNullOrEmpty(propsList), "'propsList' can't be null or empty.");

        final BatchStatement batchStatement = new BatchStatement(type == null ? BatchStatement.Type.LOGGED : type);

        if (settings != null) {
            batchStatement.setConsistencyLevel(settings.getConsistency());
            batchStatement.setSerialConsistencyLevel(settings.getSerialConsistency());
            batchStatement.setRetryPolicy(settings.getRetryPolicy());

            if (settings.traceQuery) {
                batchStatement.enableTracing();
            } else {
                batchStatement.disableTracing();
            }
        }

        CP pair = null;

        for (Map<String, Object> props : propsList) {
            pair = prepareAdd(targetClass, props);
            batchStatement.add(prepareStatement(pair.cql, pair.parameters.toArray()));
        }

        return session.execute(batchStatement);
    }

    private CP prepareAdd(final Class<?> targetClass, final Map<String, Object> props) {
        switch (namingPolicy) {
            case LOWER_CASE_WITH_UNDERSCORE:
                return NE.insert(props).into(targetClass).pair();

            case UPPER_CASE_WITH_UNDERSCORE:
                return NE2.insert(props).into(targetClass).pair();

            case CAMEL_CASE:
                return NE3.insert(props).into(targetClass).pair();

            default:
                throw new RuntimeException("Unsupported naming policy: " + namingPolicy);
        }
    }

    @SuppressWarnings("deprecation")
    public ResultSet update(final Object entity) {
        final Class<?> targetClass = entity.getClass();
        final Set<String> keyNameSet = Maps.getOrDefault(entityKeyNamesMap, targetClass, ID_SET);
        final boolean isDirtyMarker = N.isDirtyMarker(targetClass);

        if (isDirtyMarker) {
            final Map<String, Object> props = new HashMap<>();

            for (String propName : ((DirtyMarker) entity).dirtyPropNames()) {
                props.put(propName, ClassUtil.getPropValue(entity, propName));
            }

            Maps.removeAll(props, keyNameSet);

            return update(targetClass, props, entity2Cond(entity));
        } else {
            return update(targetClass, Maps.entity2Map(entity, keyNameSet), entity2Cond(entity));
        }
    }

    @SuppressWarnings("deprecation")
    public ResultSet update(final Object entity, final Collection<String> primaryKeyNames) {
        N.checkNullOrEmpty(primaryKeyNames, "primaryKeyNames");

        final Class<?> targetClass = entity.getClass();
        final Set<String> keyNameSet = new HashSet<>(N.initHashCapacity(primaryKeyNames.size()));
        final And and = new And();

        for (String keyName : primaryKeyNames) {
            String propName = ClassUtil.getPropNameByMethod(ClassUtil.getPropGetMethod(targetClass, keyName));
            and.add(L.eq(propName, ClassUtil.getPropValue(entity, propName)));
            keyNameSet.add(propName);
        }

        final boolean isDirtyMarker = N.isDirtyMarker(targetClass);

        if (isDirtyMarker) {
            final Map<String, Object> props = new HashMap<>();

            for (String propName : ((DirtyMarker) entity).dirtyPropNames()) {
                props.put(propName, ClassUtil.getPropValue(entity, propName));
            }

            Maps.removeAll(props, keyNameSet);

            return update(targetClass, props, and);
        } else {
            return update(targetClass, Maps.entity2Map(entity, keyNameSet), and);
        }
    }

    public ResultSet update(final Class<?> targetClass, final Map<String, Object> props, final Condition whereCause) {
        final CP pair = prepareUpdate(targetClass, props, whereCause);

        return this.execute(pair.cql, pair.parameters.toArray());
    }

    public ResultSet batchUpdate(final Collection<?> entities, final BatchStatement.Type type) {
        N.checkArgument(N.notNullOrEmpty(entities), "'entities' can't be null or empty.");

        final Class<?> targetClass = Seq.of(entities).first().get().getClass();
        final Set<String> keyNameSet = Maps.getOrDefault(entityKeyNamesMap, targetClass, ID_SET);
        return batchUpdate(entities, keyNameSet, type);
    }

    @SuppressWarnings("deprecation")
    public ResultSet batchUpdate(final Collection<?> entities, final Collection<String> primaryKeyNames, final BatchStatement.Type type) {
        N.checkArgument(N.notNullOrEmpty(entities), "'entities' can't be null or empty.");
        N.checkArgument(N.notNullOrEmpty(primaryKeyNames), "'primaryKeyNames' can't be null or empty");

        final Class<?> targetClass = Seq.of(entities).first().get().getClass();
        final Set<String> keyNameSet = new HashSet<>(N.initHashCapacity(primaryKeyNames.size()));

        for (String keyName : primaryKeyNames) {
            keyNameSet.add(ClassUtil.getPropNameByMethod(ClassUtil.getPropGetMethod(targetClass, keyName)));
        }

        final boolean isDirtyMarker = N.isDirtyMarker(targetClass);

        if (isDirtyMarker) {
            final List<Map<String, Object>> propsList = new ArrayList<>(entities.size());

            for (Object entity : entities) {
                final Map<String, Object> props = new HashMap<>();

                for (String propName : ((DirtyMarker) entity).dirtyPropNames()) {
                    props.put(propName, ClassUtil.getPropValue(entity, propName));
                }

                for (String keyName : keyNameSet) {
                    if (props.containsKey(keyName) == false) {
                        props.put(keyName, ClassUtil.getPropValue(entity, keyName));
                    }
                }

                propsList.add(props);
            }

            return batchUpdate(targetClass, propsList, keyNameSet, type, true);
        } else {
            return batchUpdate(targetClass, Maps.entity2Map(entities), keyNameSet, type, true);
        }
    }

    public ResultSet batchUpdate(final Class<?> targetClass, final Collection<? extends Map<String, Object>> propsList,
            final Collection<String> primaryKeyNames, final BatchStatement.Type type) {

        return batchUpdate(targetClass, propsList, primaryKeyNames, type, false);
    }

    private ResultSet batchUpdate(final Class<?> targetClass, final Collection<? extends Map<String, Object>> propsList,
            final Collection<String> primaryKeyNames, final BatchStatement.Type type, boolean isFromEntity) {
        N.checkArgument(N.notNullOrEmpty(propsList), "'propsList' can't be null or empty.");

        final BatchStatement batchStatement = new BatchStatement(type == null ? BatchStatement.Type.LOGGED : type);

        if (settings != null) {
            batchStatement.setConsistencyLevel(settings.getConsistency());
            batchStatement.setSerialConsistencyLevel(settings.getSerialConsistency());
            batchStatement.setRetryPolicy(settings.getRetryPolicy());

            if (settings.traceQuery) {
                batchStatement.enableTracing();
            } else {
                batchStatement.disableTracing();
            }
        }

        for (Map<String, Object> props : propsList) {
            final Map<String, Object> tmp = isFromEntity ? props : new HashMap<>(props);
            final And and = new And();

            for (String keyName : primaryKeyNames) {
                and.add(L.eq(keyName, tmp.remove(keyName)));
            }

            final CP pair = prepareUpdate(targetClass, tmp, and);
            batchStatement.add(prepareStatement(pair.cql, pair.parameters.toArray()));
        }

        return session.execute(batchStatement);
    }

    private CP prepareUpdate(final Class<?> targetClass, final Map<String, Object> props, final Condition whereCause) {
        switch (namingPolicy) {
            case LOWER_CASE_WITH_UNDERSCORE:
                return NE.update(targetClass).set(props).where(whereCause).pair();

            case UPPER_CASE_WITH_UNDERSCORE:
                return NE2.update(targetClass).set(props).where(whereCause).pair();

            case CAMEL_CASE:
                return NE3.update(targetClass).set(props).where(whereCause).pair();

            default:
                throw new RuntimeException("Unsupported naming policy: " + namingPolicy);
        }
    }

    public ResultSet delete(final Object entity) {
        return delete(entity, null);
    }

    public ResultSet delete(final Object entity, final Collection<String> deletingPropNames) {
        return delete(entity.getClass(), deletingPropNames, entity2Cond(entity));
    }

    @SafeVarargs
    public final ResultSet delete(final Class<?> targetClass, final Object... ids) {
        return delete(targetClass, null, ids);
    }

    /**
     * Delete the specified properties if <code>propNames</code> is not null or empty, otherwise, delete the whole record.
     * 
     * @param targetClass
     * @param deletingPropNames
     * @param id
     */
    @SafeVarargs
    public final ResultSet delete(final Class<?> targetClass, final Collection<String> deletingPropNames, final Object... ids) {
        return delete(targetClass, deletingPropNames, ids2Cond(targetClass, ids));
    }

    public ResultSet delete(final Class<?> targetClass, final Condition whereCause) {
        return delete(targetClass, null, whereCause);
    }

    /**
     * Delete the specified properties if <code>propNames</code> is not null or empty, otherwise, delete the whole record.
     * 
     * @param targetClass
     * @param deletingPropNames
     * @param whereCause
     */
    public ResultSet delete(final Class<?> targetClass, final Collection<String> deletingPropNames, final Condition whereCause) {
        final CP pair = prepareDelete(targetClass, deletingPropNames, whereCause);

        return this.execute(pair.cql, pair.parameters.toArray());
    }

    private CP prepareDelete(final Class<?> targetClass, final Collection<String> deletingPropNames, final Condition whereCause) {
        switch (namingPolicy) {
            case LOWER_CASE_WITH_UNDERSCORE:
                if (N.isNullOrEmpty(deletingPropNames)) {
                    return NE.deleteFrom(targetClass).where(whereCause).pair();
                } else {
                    return NE.delete(deletingPropNames).from(targetClass).where(whereCause).pair();
                }

            case UPPER_CASE_WITH_UNDERSCORE:
                if (N.isNullOrEmpty(deletingPropNames)) {
                    return NE2.deleteFrom(targetClass).where(whereCause).pair();
                } else {
                    return NE2.delete(deletingPropNames).from(targetClass).where(whereCause).pair();
                }

            case CAMEL_CASE:
                if (N.isNullOrEmpty(deletingPropNames)) {
                    return NE3.deleteFrom(targetClass).where(whereCause).pair();
                } else {
                    return NE3.delete(deletingPropNames).from(targetClass).where(whereCause).pair();
                }

            default:
                throw new RuntimeException("Unsupported naming policy: " + namingPolicy);
        }
    }

    @SafeVarargs
    public final boolean exists(final Class<?> targetClass, final Object... ids) {
        return exists(targetClass, ids2Cond(targetClass, ids));
    }

    public boolean exists(final Class<?> targetClass, final Condition whereCause) {
        final Collection<String> selectPropNames = Maps.getOrDefault(entityKeyNamesMap, targetClass, ID_SET);
        final CP pair = prepareQuery(targetClass, selectPropNames, whereCause, 1);
        final ResultSet resultSet = execute(pair.cql, pair.parameters.toArray());

        return resultSet.iterator().hasNext();
    }

    public long count(final Class<?> targetClass, final Condition whereCause) {
        final CP pair = prepareQuery(targetClass, N.asList(NE.COUNT_ALL), whereCause, 1);

        return count(pair.cql, pair.parameters.toArray());
    }

    public <T> List<T> find(final Class<T> targetClass, final Condition whereCause) {
        return find(targetClass, null, whereCause);
    }

    public <T> List<T> find(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause) {
        final CP pair = prepareQuery(targetClass, selectPropNames, whereCause);

        return find(targetClass, pair.cql, pair.parameters.toArray());
    }

    public <T> DataSet query(final Class<T> targetClass, final Condition whereCause) {
        return query(targetClass, null, whereCause);
    }

    public <T> DataSet query(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause) {
        final CP pair = prepareQuery(targetClass, selectPropNames, whereCause);

        return query(targetClass, pair.cql, pair.parameters.toArray());
    }

    public <T, E> NullabLe<E> queryForSingleResult(final Class<T> targetClass, final Class<E> valueClass, final String propName, final Condition whereCause) {
        final CP pair = prepareQuery(targetClass, Arrays.asList(propName), whereCause, 1);

        return queryForSingleResult(valueClass, pair.cql, pair.parameters.toArray());
    }

    public <T> Optional<T> queryForEntity(final Class<T> targetClass, final Condition whereCause) {
        return queryForEntity(targetClass, null, whereCause);
    }

    public <T> Optional<T> queryForEntity(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause) {
        final CP pair = prepareQuery(targetClass, selectPropNames, whereCause, 1);

        return queryForEntity(targetClass, pair.cql, pair.parameters.toArray());
    }

    public <T> Stream<T> stream(final Class<T> targetClass, final Condition whereCause) {
        return stream(targetClass, null, whereCause);
    }

    public <T> Stream<T> stream(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause) {
        final CP pair = prepareQuery(targetClass, selectPropNames, whereCause);

        return stream(targetClass, pair.cql, pair.parameters.toArray());
    }

    /**
     * Always remember to set "<code>LIMIT 1</code>" in the cql statement for better performance.
     *
     * @param query
     * @param parameters
     * @return
     */
    @SafeVarargs
    public final boolean exists(final String query, final Object... parameters) {
        final ResultSet resultSet = execute(query, parameters);

        return resultSet.iterator().hasNext();
    }

    @SafeVarargs
    public final long count(final String query, final Object... parameters) {
        return queryForSingleResult(long.class, query, parameters).orElse(0L);
    }

    @SafeVarargs
    public final <E> NullabLe<E> queryForSingleResult(final Class<E> valueClass, final String query, final Object... parameters) {
        final ResultSet resultSet = execute(query, parameters);
        final Row row = resultSet.one();

        return row == null ? (NullabLe<E>) NullabLe.empty() : NullabLe.of(N.as(valueClass, row.getObject(0)));
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method or <code>Map.class</code>
     * @param query
     * @param parameters
     * @return
     */
    @SafeVarargs
    public final <T> Optional<T> queryForEntity(final Class<T> targetClass, final String query, final Object... parameters) {
        final ResultSet resultSet = execute(query, parameters);
        final Row row = resultSet.one();

        return row == null ? (Optional<T>) Optional.empty() : Optional.of(toEntity(targetClass, row));
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method, <code>Map.class</code> or basic single value type(Primitive/String/Date...)
     * @param query
     * @param parameters
     * @return
     */
    @SafeVarargs
    public final <T> List<T> find(final Class<T> targetClass, final String query, final Object... parameters) {
        return toList(targetClass, execute(query, parameters));
    }

    @SafeVarargs
    public final DataSet query(final String query, final Object... parameters) {
        return extractData(execute(query, parameters));
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method or <code>Map.class</code>
     * @param query
     * @param parameters
     * @return
     */
    @SafeVarargs
    public final DataSet query(final Class<?> targetClass, final String query, final Object... parameters) {
        return extractData(targetClass, execute(query, parameters));
    }

    @SafeVarargs
    public final Stream<Row> stream(final String query, final Object... parameters) {
        return Stream.of(execute(query, parameters).iterator());
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method or <code>Map.class</code>
     * @param query
     * @param parameters
     * @return
     */
    @SafeVarargs
    public final <T> Stream<T> stream(final Class<T> targetClass, final String query, final Object... parameters) {
        return Stream.of(execute(query, parameters).iterator()).map(new Function<Row, T>() {
            @Override
            public T apply(Row row) {
                return toEntity(targetClass, row);
            }
        });
    }

    private <T> CP prepareQuery(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause) {
        return prepareQuery(targetClass, selectPropNames, whereCause, 0);
    }

    private <T> CP prepareQuery(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause, final int count) {
        CQLBuilder cqlBuilder = null;

        switch (namingPolicy) {
            case LOWER_CASE_WITH_UNDERSCORE:
                if (N.isNullOrEmpty(selectPropNames)) {
                    cqlBuilder = NE.selectFrom(targetClass).where(whereCause);
                } else {
                    cqlBuilder = NE.select(selectPropNames).from(targetClass).where(whereCause);
                }

                break;

            case UPPER_CASE_WITH_UNDERSCORE:
                if (N.isNullOrEmpty(selectPropNames)) {
                    cqlBuilder = NE2.selectFrom(targetClass).where(whereCause);
                } else {
                    cqlBuilder = NE2.select(selectPropNames).from(targetClass).where(whereCause);
                }

                break;

            case CAMEL_CASE:
                if (N.isNullOrEmpty(selectPropNames)) {
                    cqlBuilder = NE3.selectFrom(targetClass).where(whereCause);
                } else {
                    cqlBuilder = NE3.select(selectPropNames).from(targetClass).where(whereCause);
                }

                break;

            default:
                throw new RuntimeException("Unsupported naming policy: " + namingPolicy);
        }

        if (count > 0) {
            cqlBuilder.limit(count);
        }

        return cqlBuilder.pair();
    }

    public ResultSet execute(final String query) {
        return session.execute(prepareStatement(query));
    }

    @SafeVarargs
    public final ResultSet execute(final String query, final Object... parameters) {
        return session.execute(prepareStatement(query, parameters));
    }

    @SafeVarargs
    public final <T> CompletableFuture<T> asyncGet(final Class<T> targetClass, final Object... ids) {
        return asyncExecutor.execute(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return get(targetClass, ids);
            }
        });
    }

    @SafeVarargs
    public final <T> CompletableFuture<T> asyncGet(final Class<T> targetClass, final Collection<String> selectPropNames, final Object... ids)
            throws NonUniqueResultException {
        return asyncExecutor.execute(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return get(targetClass, selectPropNames, ids);
            }
        });
    }

    public <T> CompletableFuture<T> asyncGet(final Class<T> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return get(targetClass, whereCause);
            }
        });
    }

    /**
     * 
     * @param targetClass
     * @param selectPropNames
     * @param idNameVal
     * @return
     */
    public <T> CompletableFuture<T> asyncGet(final Class<T> targetClass, final Collection<String> selectPropNames, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return get(targetClass, selectPropNames, whereCause);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncInsert(final Object entity) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return insert(entity);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncInsert(final Class<?> targetClass, final Map<String, Object> props) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return insert(targetClass, props);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncBatchInsert(final Collection<?> entities, final BatchStatement.Type type) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return batchInsert(entities, type);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncBatchInsert(final Class<?> targetClass, final Collection<? extends Map<String, Object>> propsList,
            final BatchStatement.Type type) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return batchInsert(targetClass, propsList, type);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncUpdate(final Object entity) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return update(entity);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncUpdate(final Object entity, final Collection<String> primaryKeyNames) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return update(entity, primaryKeyNames);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncUpdate(final Class<?> targetClass, final Map<String, Object> props, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return update(targetClass, props, whereCause);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncBatchUpdate(final Collection<?> entities, final BatchStatement.Type type) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return batchUpdate(entities, type);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncBatchUpdate(final Collection<?> entities, final Collection<String> primaryKeyNames,
            final BatchStatement.Type type) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return batchUpdate(entities, primaryKeyNames, type);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncBatchUpdate(final Class<?> targetClass, final Collection<? extends Map<String, Object>> propsList,
            final Collection<String> primaryKeyNames, final BatchStatement.Type type) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return batchUpdate(targetClass, propsList, primaryKeyNames, type);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncDelete(final Object entity) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return delete(entity);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncDelete(final Object entity, final Collection<String> deletingPropNames) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return delete(entity, deletingPropNames);
            }
        });
    }

    @SafeVarargs
    public final CompletableFuture<ResultSet> asyncDelete(final Class<?> targetClass, final Object... ids) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return delete(targetClass, ids);
            }
        });
    }

    /**
     * Delete the specified properties if <code>propNames</code> is not null or empty, otherwise, delete the whole record.
     * 
     * @param targetClass
     * @param deletingPropNames
     * @param id
     * @return
     */
    @SafeVarargs
    public final CompletableFuture<ResultSet> asyncDelete(final Class<?> targetClass, final Collection<String> deletingPropNames, final Object... ids) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return delete(targetClass, deletingPropNames, ids);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncDelete(final Class<?> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return delete(targetClass, whereCause);
            }
        });
    }

    /**
     * Delete the specified properties if <code>propNames</code> is not null or empty, otherwise, delete the whole record.
     * 
     * @param targetClass
     * @param deletingPropNames
     * @param whereCause
     * @return
     */
    public CompletableFuture<ResultSet> asyncDelete(final Class<?> targetClass, final Collection<String> deletingPropNames, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return delete(targetClass, deletingPropNames, whereCause);
            }
        });
    }

    @SafeVarargs
    public final CompletableFuture<Boolean> asyncExists(final Class<?> targetClass, final Object... ids) {
        return asyncExecutor.execute(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return exists(targetClass, ids);
            }
        });
    }

    public CompletableFuture<Boolean> asyncExists(final Class<?> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return exists(targetClass, whereCause);
            }
        });
    }

    public CompletableFuture<Long> asyncCount(final Class<?> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return count(targetClass, whereCause);
            }
        });
    }

    public <T> CompletableFuture<List<T>> asyncFind(final Class<T> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<List<T>>() {
            @Override
            public List<T> call() throws Exception {
                return find(targetClass, whereCause);
            }
        });
    }

    public <T> CompletableFuture<List<T>> asyncFind(final Class<T> targetClass, final Collection<String> selectPropName, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<List<T>>() {
            @Override
            public List<T> call() throws Exception {
                return find(targetClass, selectPropName, whereCause);
            }
        });
    }

    public <T> CompletableFuture<DataSet> asyncQuery(final Class<T> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<DataSet>() {
            @Override
            public DataSet call() throws Exception {
                return query(targetClass, whereCause);
            }
        });
    }

    public <T> CompletableFuture<DataSet> asyncQuery(final Class<T> targetClass, final Collection<String> selectPropName, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<DataSet>() {
            @Override
            public DataSet call() throws Exception {
                return query(targetClass, selectPropName, whereCause);
            }
        });
    }

    public <T, E> CompletableFuture<NullabLe<E>> asyncQueryForSingleResult(final Class<T> targetClass, final Class<E> valueClass, final String propName,
            final Condition whereCause) {
        return asyncExecutor.execute(new Callable<NullabLe<E>>() {
            @Override
            public NullabLe<E> call() throws Exception {
                return queryForSingleResult(targetClass, valueClass, propName, whereCause);
            }
        });
    }

    public <T> CompletableFuture<Optional<T>> asyncQueryForEntity(final Class<T> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<Optional<T>>() {
            @Override
            public Optional<T> call() throws Exception {
                return queryForEntity(targetClass, whereCause);
            }
        });
    }

    public <T> CompletableFuture<Optional<T>> asyncQueryForEntity(final Class<T> targetClass, final Collection<String> selectPropName,
            final Condition whereCause) {
        return asyncExecutor.execute(new Callable<Optional<T>>() {
            @Override
            public Optional<T> call() throws Exception {
                return queryForEntity(targetClass, selectPropName, whereCause);
            }
        });
    }

    public <T> CompletableFuture<Stream<T>> asyncStream(final Class<T> targetClass, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<Stream<T>>() {
            @Override
            public Stream<T> call() throws Exception {
                return stream(targetClass, whereCause);
            }
        });
    }

    public <T> CompletableFuture<Stream<T>> asyncStream(final Class<T> targetClass, final Collection<String> selectPropName, final Condition whereCause) {
        return asyncExecutor.execute(new Callable<Stream<T>>() {
            @Override
            public Stream<T> call() throws Exception {
                return stream(targetClass, selectPropName, whereCause);
            }
        });
    }

    /**
     * Always remember to set "<code>LIMIT 1</code>" in the cql statement for better performance.
     *
     * @param query
     * @param parameters
     * @return
     */
    @SafeVarargs
    public final CompletableFuture<Boolean> asyncExists(final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return exists(query, parameters);
            }
        });
    }

    @SafeVarargs
    public final CompletableFuture<Long> asyncCount(final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return count(query, parameters);
            }
        });
    }

    @SafeVarargs
    public final <T> CompletableFuture<NullabLe<T>> asyncQueryForSingleResult(final Class<T> targetClass, final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<NullabLe<T>>() {
            @Override
            public NullabLe<T> call() throws Exception {
                return queryForSingleResult(targetClass, query, parameters);
            }
        });
    }

    @SafeVarargs
    public final <T> CompletableFuture<Optional<T>> asyncQueryForEntity(final Class<T> targetClass, final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<Optional<T>>() {
            @Override
            public Optional<T> call() throws Exception {
                return queryForEntity(targetClass, query, parameters);
            }
        });
    }

    @SafeVarargs
    public final <T> CompletableFuture<List<T>> asyncFind(final Class<T> targetClass, final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<List<T>>() {
            @Override
            public List<T> call() throws Exception {
                return find(targetClass, query, parameters);
            }
        });
    }

    @SafeVarargs
    public final CompletableFuture<DataSet> asyncQuery(final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<DataSet>() {
            @Override
            public DataSet call() throws Exception {
                return query(query, parameters);
            }
        });
    }

    @SafeVarargs
    public final CompletableFuture<DataSet> asyncQuery(final Class<?> targetClass, final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<DataSet>() {
            @Override
            public DataSet call() throws Exception {
                return query(targetClass, query, parameters);
            }
        });
    }

    @SafeVarargs
    public final CompletableFuture<Stream<Row>> asyncStream(final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<Stream<Row>>() {
            @Override
            public Stream<Row> call() throws Exception {
                return stream(query, parameters);
            }
        });
    }

    /**
     * 
     * @param targetClass an entity class with getter/setter method or <code>Map.class</code>
     * @param query
     * @param parameters
     * @return
     */
    @SafeVarargs
    public final <T> CompletableFuture<Stream<T>> asyncStream(final Class<T> targetClass, final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<Stream<T>>() {
            @Override
            public Stream<T> call() throws Exception {
                return stream(targetClass, query, parameters);
            }
        });
    }

    public CompletableFuture<ResultSet> asyncExecute(final String query) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() throws Exception {
                return execute(query);
            }
        });
    }

    @SafeVarargs
    public final CompletableFuture<ResultSet> asyncExecute(final String query, final Object... parameters) {
        return asyncExecutor.execute(new Callable<ResultSet>() {
            @Override
            public ResultSet call() throws Exception {
                return execute(query, parameters);
            }
        });
    }

    @Override
    public void close() throws IOException {
        try {
            if (session.isClosed() == false) {
                session.close();
            }
        } finally {
            if (cluster.isClosed() == false) {
                cluster.close();
            }
        }
    }

    private static <T> void checkTargetClass(final Class<T> targetClass) {
        if (!(N.isEntity(targetClass) || Map.class.isAssignableFrom(targetClass))) {
            throw new IllegalArgumentException("The target class must be an entity class with getter/setter methods or Map.class. But it is: "
                    + ClassUtil.getCanonicalClassName(targetClass));
        }
    }

    private Statement prepareStatement(final String query) {
        Statement stmt = null;

        if (query.length() <= POOLABLE_LENGTH) {
            PoolableWrapper<Statement> wrapper = stmtPool.get(query);

            if (wrapper != null) {
                stmt = wrapper.value();
            }
        }

        if (stmt == null) {
            final NamedCQL namedCQL = getNamedCQL(query);
            final String cql = namedCQL.getPureCQL();
            stmt = bind(prepare(cql));

            if (query.length() <= POOLABLE_LENGTH) {
                stmtPool.put(query, PoolableWrapper.of(stmt));
            }
        }

        return stmt;
    }

    private Statement prepareStatement(String query, Object... parameters) {
        if (N.isNullOrEmpty(parameters)) {
            return prepareStatement(query);
        }

        final NamedCQL namedCQL = getNamedCQL(query);
        final String cql = namedCQL.getPureCQL();
        PreparedStatement preStmt = null;

        if (query.length() <= POOLABLE_LENGTH) {
            PoolableWrapper<PreparedStatement> wrapper = preStmtPool.get(query);
            if (wrapper != null && wrapper.value() != null) {
                preStmt = wrapper.value();
            }
        }

        if (preStmt == null) {
            preStmt = prepare(cql);

            if (query.length() <= POOLABLE_LENGTH) {
                preStmtPool.put(query, PoolableWrapper.of(preStmt));
            }
        }

        final ColumnDefinitions columnDefinitions = preStmt.getVariables();
        final int parameterCount = columnDefinitions.size();

        if (parameterCount == 0) {
            return preStmt.bind();
        } else if (N.isNullOrEmpty(parameters)) {
            throw new IllegalArgumentException("Null or empty parameters for parameterized query: " + query);
        }

        if (parameterCount == 1 && parameters.length == 1) {
            Class<?> javaClass = namedDataType.get(columnDefinitions.getType(0).getName().name());

            if (parameters[0] == null || javaClass.isAssignableFrom(parameters[0].getClass())) {
                return bind(preStmt, parameters);
            } else if (parameters[0] instanceof List && ((List<Object>) parameters[0]).size() == 1) {
                final Object tmp = ((List<Object>) parameters[0]).get(0);

                if (tmp == null || javaClass.isAssignableFrom(tmp.getClass())) {
                    return bind(preStmt, tmp);
                }
            }
        }

        Object[] values = parameters;

        if (parameters.length == 1 && (parameters[0] instanceof Map || N.isEntity(parameters[0].getClass()))) {
            values = new Object[parameterCount];
            final Object parameter_0 = parameters[0];
            final Map<Integer, String> namedParameters = namedCQL.getNamedParameters();
            final boolean isCassandraNamedParameters = N.isNullOrEmpty(namedParameters);
            String parameterName = null;
            if (parameter_0 instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) parameter_0;

                for (int i = 0; i < parameterCount; i++) {
                    parameterName = isCassandraNamedParameters ? columnDefinitions.getName(i) : namedParameters.get(i);
                    values[i] = m.get(parameterName);

                    if ((values[i] == null) && !m.containsKey(parameterName)) {
                        throw new IllegalArgumentException("Parameter for property '" + parameterName + "' is missed");
                    }
                }
            } else {
                Object entity = parameter_0;
                Class<?> clazz = entity.getClass();
                Method propGetMethod = null;

                for (int i = 0; i < parameterCount; i++) {
                    parameterName = isCassandraNamedParameters ? columnDefinitions.getName(i) : namedParameters.get(i);
                    propGetMethod = ClassUtil.getPropGetMethod(clazz, parameterName);

                    if (propGetMethod == null) {
                        throw new IllegalArgumentException("Parameter for property '" + parameterName + "' is missed");
                    }

                    values[i] = ClassUtil.invokeMethod(entity, propGetMethod);
                }
            }
        } else if ((parameters.length == 1) && (parameters[0] != null)) {
            if (parameters[0] instanceof Object[] && ((((Object[]) parameters[0]).length) >= namedCQL.getParameterCount())) {
                values = (Object[]) parameters[0];
            } else if (parameters[0] instanceof List && (((List<?>) parameters[0]).size() >= namedCQL.getParameterCount())) {
                final Collection<?> c = (Collection<?>) parameters[0];
                values = c.toArray(new Object[c.size()]);
            }
        }

        Class<?> javaClass = null;
        for (int i = 0; i < parameterCount; i++) {
            javaClass = namedDataType.get(columnDefinitions.getType(i).getName().name());

            if (values[i] == null) {
                values[i] = N.defaultValueOf(javaClass);
            } else if (!javaClass.isAssignableFrom(values[i].getClass())) {
                try {
                    values[i] = N.as(javaClass, values[i]);
                } catch (Throwable e) {
                    // ignore.
                }
            }
        }

        return bind(preStmt, values.length == parameterCount ? values : N.copyOfRange(values, 0, parameterCount));
    }

    private PreparedStatement prepare(final String query) {
        PreparedStatement preStat = session.prepare(query);

        if (settings != null) {
            if (settings.getConsistency() != null) {
                preStat.setConsistencyLevel(settings.getConsistency());
            }

            if (settings.getSerialConsistency() != null) {
                preStat.setSerialConsistencyLevel(settings.getSerialConsistency());
            }

            if (settings.getRetryPolicy() != null) {
                preStat.setRetryPolicy(settings.getRetryPolicy());
            }

            if (settings.isTraceQuery()) {
                preStat.enableTracing();
            } else {
                preStat.disableTracing();
            }
        }

        return preStat;
    }

    private BoundStatement bind(PreparedStatement preStmt) {
        BoundStatement stmt = preStmt.bind();

        if (settings != null && settings.getFetchSize() > 0) {
            stmt.setFetchSize(settings.getFetchSize());
        }

        return stmt;
    }

    private BoundStatement bind(PreparedStatement preStmt, Object... parameters) {
        BoundStatement stmt = preStmt.bind(parameters);

        if (settings != null && settings.getFetchSize() > 0) {
            stmt.setFetchSize(settings.getFetchSize());
        }

        return stmt;
    }

    private NamedCQL getNamedCQL(String cql) {
        NamedCQL namedCQL = null;

        if (cqlMapper != null) {
            namedCQL = cqlMapper.get(cql);
        }

        if (namedCQL == null) {
            namedCQL = NamedCQL.parse(cql, null);
        }

        return namedCQL;
    }

    /**
     * <pre>
     * <code>   static final CassandraExecutor cassandraExecutor;
    
    static {
        final CodecRegistry codecRegistry = new CodecRegistry();
        final Cluster cluster = Cluster.builder().withCodecRegistry(codecRegistry).addContactPoint("127.0.0.1").build();
    
        codecRegistry.register(new UDTCodec&lt;Address&gt;(cluster, "simplex", "address", Address.class) {
            protected Address deserialize(UDTValue value) {
                if (value == null) {
                    return null;
                }
                Address address = new Address();
                address.setStreet(value.getString("street"));
                address.setCity(value.getString("city"));
                address.setZipCode(value.getInt("zipCode"));
                return address;
            }
    
            protected UDTValue serialize(Address value) {
                return value == null ? null
                        : newUDTValue().setString("street", value.getStreet()).setInt("zipcode", value.getZipCode());
            }
        });
    
    
        cassandraExecutor = new CassandraExecutor(cluster);
    }
     * </code>
     * </pre>
     * 
     * @author haiyangl
     *
     * @param <T>
     */
    public abstract static class UDTCodec<T> extends TypeCodec<T> {
        private final TypeCodec<UDTValue> innerCodec;
        private final UserType userType;
        private final Class<T> javaType;

        public UDTCodec(TypeCodec<UDTValue> innerCodec, Class<T> javaType) {
            super(innerCodec.getCqlType(), javaType);
            this.innerCodec = innerCodec;
            this.userType = (UserType) innerCodec.getCqlType();
            this.javaType = javaType;
        }

        public UDTCodec(final Cluster cluster, final String keySpace, final String userType, Class<T> javaType) {
            this(TypeCodec.userType(cluster.getMetadata().getKeyspace(keySpace).getUserType(userType)), javaType);
        }

        @Override
        public ByteBuffer serialize(T value, ProtocolVersion protocolVersion) throws InvalidTypeException {
            return innerCodec.serialize(serialize(value), protocolVersion);
        }

        @Override
        public T deserialize(ByteBuffer bytes, ProtocolVersion protocolVersion) throws InvalidTypeException {
            return deserialize(innerCodec.deserialize(bytes, protocolVersion));
        }

        @Override
        public T parse(String value) throws InvalidTypeException {
            return N.isNullOrEmpty(value) ? null : N.fromJSON(javaType, value);
        }

        @Override
        public String format(T value) throws InvalidTypeException {
            return value == null ? null : N.toJSON(value);
        }

        protected UDTValue newUDTValue() {
            return userType.newValue();
        }

        protected abstract UDTValue serialize(T value);

        protected abstract T deserialize(UDTValue value);
    }

    public static final class StatementSettings {
        private ConsistencyLevel consistency;
        private ConsistencyLevel serialConsistency;
        private boolean traceQuery;
        private RetryPolicy retryPolicy;
        private int fetchSize;

        public StatementSettings() {
        }

        public StatementSettings(ConsistencyLevel consistency, ConsistencyLevel serialConsistency, boolean traceQuery, RetryPolicy retryPolicy, int fetchSize) {
            this.consistency = consistency;
            this.serialConsistency = serialConsistency;
            this.traceQuery = traceQuery;
            this.retryPolicy = retryPolicy;
            this.fetchSize = fetchSize;
        }

        public static StatementSettings create() {
            return new StatementSettings();
        }

        public ConsistencyLevel getConsistency() {
            return consistency;
        }

        public StatementSettings setConsistency(ConsistencyLevel consistency) {
            this.consistency = consistency;

            return this;
        }

        public ConsistencyLevel getSerialConsistency() {
            return serialConsistency;
        }

        public StatementSettings setSerialConsistency(ConsistencyLevel serialConsistency) {
            this.serialConsistency = serialConsistency;

            return this;
        }

        public boolean isTraceQuery() {
            return traceQuery;
        }

        public StatementSettings setTraceQuery(boolean traceQuery) {
            this.traceQuery = traceQuery;

            return this;
        }

        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }

        public StatementSettings setRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;

            return this;
        }

        public int getFetchSize() {
            return fetchSize;
        }

        public StatementSettings setFetchSize(int fetchSize) {
            this.fetchSize = fetchSize;

            return this;
        }

        public StatementSettings copy() {
            StatementSettings copy = new StatementSettings();

            copy.consistency = this.consistency;
            copy.serialConsistency = this.serialConsistency;
            copy.traceQuery = this.traceQuery;
            copy.retryPolicy = this.retryPolicy;
            copy.fetchSize = this.fetchSize;

            return copy;
        }

        @Override
        public int hashCode() {
            int h = 17;
            h = 31 * h + N.hashCode(consistency);
            h = 31 * h + N.hashCode(serialConsistency);
            h = 31 * h + N.hashCode(traceQuery);
            h = 31 * h + N.hashCode(retryPolicy);
            h = 31 * h + N.hashCode(fetchSize);

            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof StatementSettings) {
                StatementSettings other = (StatementSettings) obj;

                if (N.equals(consistency, other.consistency) && N.equals(serialConsistency, other.serialConsistency) && N.equals(traceQuery, other.traceQuery)
                        && N.equals(retryPolicy, other.retryPolicy) && N.equals(fetchSize, other.fetchSize)) {

                    return true;
                }
            }

            return false;
        }

        @Override
        public String toString() {
            return "{" + "consistency=" + N.toString(consistency) + ", " + "serialConsistency=" + N.toString(serialConsistency) + ", " + "traceQuery="
                    + N.toString(traceQuery) + ", " + "retryPolicy=" + N.toString(retryPolicy) + ", " + "fetchSize=" + N.toString(fetchSize) + "}";
        }
    }
}

/*
 * Copyright 2008-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.geequery.springdata.repository.query;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.QueryHint;
import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import javax.persistence.TypedQuery;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.util.Assert;

import com.github.geequery.springdata.repository.EntityGraph;
import com.github.geequery.springdata.repository.query.JpaQueryExecution.CollectionExecution;
import com.github.geequery.springdata.repository.query.JpaQueryExecution.ModifyingExecution;
import com.github.geequery.springdata.repository.query.JpaQueryExecution.PagedExecution;
import com.github.geequery.springdata.repository.query.JpaQueryExecution.ProcedureExecution;
import com.github.geequery.springdata.repository.query.JpaQueryExecution.SingleEntityExecution;
import com.github.geequery.springdata.repository.query.JpaQueryExecution.SlicedExecution;

/**
 * Abstract base class to implement {@link RepositoryQuery}s.
 * 
 * @author Oliver Gierke
 * @author Thomas Darimont
 */
public abstract class AbstractJpaQuery implements RepositoryQuery {

	private final JpaQueryMethod method;
	private final EntityManager em;

	/**
	 * Creates a new {@link AbstractJpaQuery} from the given {@link JpaQueryMethod}.
	 * 
	 * @param method
	 * @param resultFactory
	 * @param em
	 */
	public AbstractJpaQuery(JpaQueryMethod method, EntityManager em) {

		Assert.notNull(method);
		Assert.notNull(em);

		this.method = method;
		this.em = em;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.query.RepositoryQuery#getQueryMethod()
	 */
	public JpaQueryMethod getQueryMethod() {
		return method;
	}

	/**
	 * Returns the {@link EntityManager}.
	 * 
	 * @return will never be {@literal null}.
	 */
	protected EntityManager getEntityManager() {
		return em;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.query.RepositoryQuery#execute(java.lang.Object[])
	 */
	public Object execute(Object[] parameters) {
		return doExecute(getExecution(), parameters);
	}

	/**
	 * @param execution
	 * @param values
	 * @return
	 */
	private Object doExecute(JpaQueryExecution execution, Object[] values) {

		Object result = execution.execute(this, values);

		ParametersParameterAccessor accessor = new ParametersParameterAccessor(method.getParameters(), values);
		ResultProcessor withDynamicProjection = method.getResultProcessor().withDynamicProjection(accessor);

		return withDynamicProjection.processResult(result, TupleConverter.INSTANCE);
	}

	protected JpaQueryExecution getExecution() {

		if (method.isStreamQuery()) {
//			return new StreamExecution();
			throw new UnsupportedOperationException();
		} else if (method.isProcedureQuery()) {
			return new ProcedureExecution();
		} else if (method.isCollectionQuery()) {
			return new CollectionExecution();
		} else if (method.isSliceQuery()) {
			return new SlicedExecution(method.getParameters());
		} else if (method.isPageQuery()) {
			return new PagedExecution(method.getParameters());
		} else if (method.isModifyingQuery()) {
			return method.getClearAutomatically() ? new ModifyingExecution(method, em) : new ModifyingExecution(method, null);
		} else {
			return new SingleEntityExecution();
		}
	}

	/**
	 * Applies the declared query hints to the given query.
	 * 
	 * @param query
	 * @return
	 */
	protected <T extends Query> T applyHints(T query, JpaQueryMethod method) {

		for (QueryHint hint : method.getHints()) {
			applyQueryHint(query, hint);
		}

		return query;
	}

	/**
	 * Protected to be able to customize in sub-classes.
	 * 
	 * @param query must not be {@literal null}.
	 * @param hint must not be {@literal null}.
	 */
	protected <T extends Query> void applyQueryHint(T query, QueryHint hint) {

		Assert.notNull(query, "Query must not be null!");
		Assert.notNull(hint, "QueryHint must not be null!");

		query.setHint(hint.name(), hint.value());
	}

	/**
	 * Applies the {@link LockModeType} provided by the {@link JpaQueryMethod} to the given {@link Query}.
	 * 
	 * @param query must not be {@literal null}.
	 * @param method must not be {@literal null}.
	 * @return
	 */
	private Query applyLockMode(Query query, JpaQueryMethod method) {

		LockModeType lockModeType = method.getLockModeType();
		return lockModeType == null ? query : query.setLockMode(lockModeType);
	}

	protected ParameterBinder createBinder(Object[] values) {
		return new ParameterBinder(getQueryMethod().getParameters(), values);
	}

	protected Query createQuery(Object[] values) {
		return applyLockMode(applyEntityGraphConfiguration(applyHints(doCreateQuery(values), method), method), method);
	}

	/**
	 * Configures the {@link javax.persistence.EntityGraph} to use for the given {@link JpaQueryMethod} if the
	 * {@link EntityGraph} annotation is present.
	 * 
	 * @param query must not be {@literal null}.
	 * @param method must not be {@literal null}.
	 * @return
	 */
	private Query applyEntityGraphConfiguration(Query query, JpaQueryMethod method) {

		Assert.notNull(query, "Query must not be null!");
		Assert.notNull(method, "JpaQueryMethod must not be null!");

		Map<String, Object> hints = Jpa21Utils.tryGetFetchGraphHints(em, method.getEntityGraph(),
				getQueryMethod().getEntityInformation().getJavaType());

		for (Map.Entry<String, Object> hint : hints.entrySet()) {
			query.setHint(hint.getKey(), hint.getValue());
		}

		return query;
	}

	protected Query createCountQuery(Object[] values) {
		Query countQuery = doCreateCountQuery(values);
		return method.applyHintsToCountQuery() ? applyHints(countQuery, method) : countQuery;
	}

	/**
	 * Creates a {@link Query} instance for the given values.
	 * 
	 * @param values must not be {@literal null}.
	 * @return
	 */
	protected abstract Query doCreateQuery(Object[] values);

	/**
	 * Creates a {@link TypedQuery} for counting using the given values.
	 * 
	 * @param values must not be {@literal null}.
	 * @return
	 */
	protected abstract Query doCreateCountQuery(Object[] values);

	private static enum TupleConverter implements Converter<Object, Object> {

		INSTANCE;

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Object convert(Object source) {

			if (!(source instanceof Tuple)) {
				return source;
			}

			Tuple tuple = (Tuple) source;
			Map<String, Object> result = new HashMap<String, Object>();

			for (TupleElement<?> element : tuple.getElements()) {

				String alias = element.getAlias();

				if (alias == null || isIndexAsString(alias)) {
					throw new IllegalStateException("No aliases found in result tuple! Make sure your query defines aliases!");
				}

				result.put(element.getAlias(), tuple.get(element));
			}

			return result;
		}

		private static boolean isIndexAsString(String source) {

			try {
				Integer.parseInt(source);
				return true;
			} catch (NumberFormatException o_O) {
				return false;
			}
		}
	}
}

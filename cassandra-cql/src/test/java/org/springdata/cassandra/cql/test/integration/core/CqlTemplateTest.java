/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springdata.cassandra.cql.test.integration.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.springdata.cassandra.cql.core.HostMapper;
import org.springdata.cassandra.cql.core.QueryCreator;
import org.springdata.cassandra.cql.core.ResultSetExtractor;
import org.springdata.cassandra.cql.core.RingMember;
import org.springdata.cassandra.cql.core.RowCallbackHandler;
import org.springdata.cassandra.cql.core.RowMapper;
import org.springdata.cassandra.cql.core.SessionCallback;
import org.springdata.cassandra.cql.core.SimpleQueryCreator;
import org.springframework.dao.DataIntegrityViolationException;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Query;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.collect.ImmutableList;

/**
 * @author David Webb
 * @author Alex Shvid
 */
public class CqlTemplateTest extends AbstractCassandraOperations {

	@Test
	public void ringTest() {

		Collection<RingMember> ring = cqlTemplate.describeRing();

		/*
		 * There must be 1 node in the cluster if the embedded server is
		 * running.
		 */
		assertNotNull(ring);

		for (RingMember h : ring) {
			log.info("ringTest Host -> " + h.address);
		}
	}

	@Test
	public void hostMapperTest() {

		List<MyHost> ring = cqlTemplate.describeRing(new HostMapper<MyHost>() {

			@Override
			public MyHost mapHost(Host host) {
				MyHost mh = new MyHost();
				mh.someName = host.getAddress().getCanonicalHostName();
				return mh;
			}

		});

		assertNotNull(ring);
		assertTrue(ring.size() > 0);

		for (MyHost h : ring) {
			log.info("hostMapperTest Host -> " + h.someName);
		}

	}

	@Test
	public void executeTestSessionCallback() {

		final String isbn = UUID.randomUUID().toString();
		final String title = "Spring Data Cassandra Cookbook";
		final String author = "David Webb";
		final Integer pages = 1;

		cqlTemplate.execute(new SessionCallback<Object>() {

			@Override
			public Object doInSession(Session s) {

				String cql = "insert into book (isbn, title, author, pages) values (?, ?, ?, ?)";

				PreparedStatement ps = s.prepare(cql);
				BoundStatement bs = ps.bind(isbn, title, author, pages);

				s.execute(bs);

				return null;

			}
		});

		Book b = getBook(isbn);

		assertBook(b, isbn, title, author, pages);

	}

	@Test
	public void executeTestCqlString() {

		final String isbn = UUID.randomUUID().toString();
		final String title = "Spring Data Cassandra Cookbook";
		final String author = "David Webb";
		final Integer pages = 1;

		cqlTemplate.getUpdateOperation(
				"insert into book (isbn, title, author, pages) values ('" + isbn + "', '" + title + "', '" + author + "', "
						+ pages + ")").execute();

		Book b = getBook(isbn);

		assertBook(b, isbn, title, author, pages);

	}

	@Test
	public void queryTestCqlStringResultSetCallback() {

		final String isbn = "999999999";

		Book b1 = cqlTemplate.getSelectOperation("select * from book where isbn='" + isbn + "'")
				.transform(new ResultSetExtractor<Book>() {

					@Override
					public Book extractData(ResultSet rs) {
						Row r = rs.one();
						assertNotNull(r);

						Book b = rowToBook(r);

						return b;
					}
				}).execute();

		Book b2 = getBook(isbn);

		assertBook(b1, b2);

	}

	@Test
	public void queryTestCqlStringRowCallbackHandler() {

		final String isbn = "999999999";

		final Book b1 = getBook(isbn);

		cqlTemplate.getSelectOperation("select * from book where isbn='" + isbn + "'").forEach(new RowCallbackHandler() {

			@Override
			public void processRow(Row row) {

				assertNotNull(row);

				Book b = rowToBook(row);

				assertBook(b1, b);

			}
		}).execute();

	}

	@Test
	public void processTestResultSetRowCallbackHandler() {

		final String isbn = "999999999";

		final Book b1 = getBook(isbn);

		ResultSet rs = cqlTemplate.getSelectOperation(new SimpleQueryCreator("select * from book where isbn='" + isbn + "'"))
				.executeAsync().getUninterruptibly();

		assertNotNull(rs);

		cqlTemplate.process(rs, new RowCallbackHandler() {

			@Override
			public void processRow(Row row) {

				assertNotNull(row);

				Book b = rowToBook(row);

				assertBook(b1, b);

			}

		});

	}

	@Test
	public void queryTestCqlStringRowMapper() {

		// Insert our 3 test books.
		insertBooks();

		List<Book> books = cqlTemplate.getSelectOperation("select * from book where isbn in ('1234','2345','3456')")
				.map(new RowMapper<Book>() {

					@Override
					public Book mapRow(Row row, int rowNum) {
						Book b = rowToBook(row);
						return b;
					}
				}).execute();

		log.debug("Size of Book List -> " + books.size());
		assertEquals(books.size(), 3);
		assertBook(books.get(0), getBook(books.get(0).getIsbn()));
		assertBook(books.get(1), getBook(books.get(1).getIsbn()));
		assertBook(books.get(2), getBook(books.get(2).getIsbn()));
	}

	@Test
	public void processTestResultSetRowMapper() {

		// Insert our 3 test books.
		insertBooks();

		ResultSet rs = cqlTemplate
				.getSelectOperation(new SimpleQueryCreator("select * from book where isbn in ('1234','2345','3456')")).executeAsync()
				.getUninterruptibly();

		assertNotNull(rs);

		List<Book> books = cqlTemplate.process(rs, new RowMapper<Book>() {

			@Override
			public Book mapRow(Row row, int rowNum) {
				Book b = rowToBook(row);
				return b;
			}
		});

		log.debug("Size of Book List -> " + books.size());
		assertEquals(books.size(), 3);
		assertBook(books.get(0), getBook(books.get(0).getIsbn()));
		assertBook(books.get(1), getBook(books.get(1).getIsbn()));
		assertBook(books.get(2), getBook(books.get(2).getIsbn()));

	}

	@Test
	public void queryForObjectTestCqlStringRowMapper() {

		Book book = cqlTemplate.getSelectOperation("select * from book where isbn in ('" + ISBN_NINES + "')").singleResult()
				.map(new RowMapper<Book>() {
					@Override
					public Book mapRow(Row row, int rowNum) {
						Book b = rowToBook(row);
						return b;
					}
				}).execute();

		assertNotNull(book);
		assertBook(book, getBook(ISBN_NINES));
	}

	/**
	 * Test that CQL for QueryForObject must only return 1 row or an IllegalArgumentException is thrown.
	 */
	@Test(expected = DataIntegrityViolationException.class)
	public void queryForObjectTestCqlStringRowMapperNotOneRowReturned() {

		// Insert our 3 test books.
		insertBooks();

		Book book = cqlTemplate.getSelectOperation(new SimpleQueryCreator("select * from book where isbn in ('1234','2345','3456')"))
				.singleResult().map(new RowMapper<Book>() {
					@Override
					public Book mapRow(Row row, int rowNum) {
						Book b = rowToBook(row);
						return b;
					}
				}).execute();
	}

	@Test
	public void processOneTestResultSetRowMapper() {

		// Insert our 3 test books.
		insertBooks();

		ResultSet rs = cqlTemplate
				.getSelectOperation(new SimpleQueryCreator("select * from book where isbn in ('" + ISBN_NINES + "')")).executeAsync()
				.getUninterruptibly();

		assertNotNull(rs);

		Book book = cqlTemplate.processOne(rs, new RowMapper<Book>() {
			@Override
			public Book mapRow(Row row, int rowNum) {
				Book b = rowToBook(row);
				return b;
			}
		}, true);

		assertNotNull(book);
		assertBook(book, getBook(ISBN_NINES));
	}

	@Test
	public void quertForObjectTestCqlStringRequiredType() {

		String title = cqlTemplate.getSelectOperation("select title from book where isbn in ('" + ISBN_NINES + "')").singleResult()
				.firstColumn(String.class).execute();

		assertEquals(title, TITLE_NINES);

	}

	@Test(expected = ClassCastException.class)
	public void queryForObjectTestCqlStringRequiredTypeInvalid() {

		Float title = cqlTemplate
				.getSelectOperation(new SimpleQueryCreator("select title from book where isbn in ('" + ISBN_NINES + "')")).singleResult()
				.firstColumn(Float.class).execute();

	}

	@Test
	public void processOneTestResultSetType() {

		ResultSet rs = cqlTemplate
				.getSelectOperation(new SimpleQueryCreator("select title from book where isbn in ('" + ISBN_NINES + "')")).executeAsync()
				.getUninterruptibly();

		assertNotNull(rs);

		String title = cqlTemplate.processOneFirstColumn(rs, String.class, true);

		assertNotNull(title);
		assertEquals(title, TITLE_NINES);
	}

	@Test
	public void queryForMapTestCqlString() {

		Map<String, Object> rsMap = cqlTemplate.getSelectOperation("select * from book where isbn in ('" + ISBN_NINES + "')")
				.singleResult().map().execute();

		log.debug(rsMap.toString());

		Book b1 = objectToBook(rsMap.get("isbn"), rsMap.get("title"), rsMap.get("author"), rsMap.get("pages"));

		Book b2 = getBook(ISBN_NINES);

		assertBook(b1, b2);

	}

	@Test
	public void processMapTestResultSet() {

		ResultSet rs = cqlTemplate
				.getSelectOperation(new SimpleQueryCreator("select * from book where isbn in ('" + ISBN_NINES + "')")).executeAsync()
				.getUninterruptibly();

		assertNotNull(rs);

		Map<String, Object> rsMap = cqlTemplate.processOneAsMap(rs, true);

		log.debug("Size of Book List -> " + rsMap.size());

		Book b1 = objectToBook(rsMap.get("isbn"), rsMap.get("title"), rsMap.get("author"), rsMap.get("pages"));

		Book b2 = getBook(ISBN_NINES);

		assertBook(b1, b2);

	}

	@Test
	public void queryForListTestCqlStringType() {

		// Insert our 3 test books.
		insertBooks();

		List<String> titles = cqlTemplate.getSelectOperation("select title from book where isbn in ('1234','2345','3456')")
				.firstColumn(String.class).execute();

		log.debug(titles.toString());

		assertNotNull(titles);

		assertEquals(titles.size(), 3);

	}

	@Test
	public void processListTestResultSetType() {

		// Insert our 3 test books.
		insertBooks();

		ResultSet rs = cqlTemplate.getSelectOperation("select * from book where isbn in ('1234','2345','3456')").executeAsync()
				.getUninterruptibly();

		assertNotNull(rs);

		List<String> titles = ImmutableList.copyOf(cqlTemplate.processFirstColumn(rs, String.class));

		log.debug(titles.toString());

		assertNotNull(titles);
		assertEquals(titles.size(), 3);
	}

	@Test
	public void queryForListOfMapCqlString() {

		// Insert our 3 test books.
		insertBooks();

		List<Map<String, Object>> results = cqlTemplate.getSelectOperation("select * from book where isbn in ('1234','2345','3456')")
				.map().execute();

		log.debug(results.toString());

		assertEquals(results.size(), 3);

	}

	@Test
	public void countTest() {

		cqlTemplate.getTruncateOperation("book").execute();

		// Insert our 3 test books.
		insertBooks();

		Long count = cqlTemplate.getSelectOperation(new QueryCreator() {

			@Override
			public Query createQuery() {
				return QueryBuilder.select().countAll().from("book");
			}

		}).singleResult().firstColumn(Long.class).execute();

		log.info("Book Count -> " + count);

		assertEquals(new Long(3), count);

	}

	@Test
	public void processListOfMapTestResultSet() {

		// Insert our 3 test books.
		insertBooks();

		ResultSet rs = cqlTemplate.getSelectOperation("select * from book where isbn in ('1234','2345','3456')").executeAsync()
				.getUninterruptibly();

		assertNotNull(rs);

		List<Map<String, Object>> results = ImmutableList.copyOf(cqlTemplate.processAsMap(rs));

		log.debug(results.toString());

		assertEquals(results.size(), 3);

	}

	/**
	 * For testing a HostMapper Implementation
	 */
	public class MyHost {
		public String someName;
	}

}

/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2024 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.test.griffin.engine.functions.groupby;

import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.cairo.sql.RecordMetadata;
import io.questdb.std.str.DirectUtf8String;
import io.questdb.std.str.Utf8Sequence;
import io.questdb.test.AbstractCairoTest;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Test;

public class FirstAndLastStrGroupByFunctionFactoryTest extends AbstractCairoTest {

    @Test
    public void testAllNull() throws Exception {
        assertQuery(
                "r1\tr2\n" +
                        "\t\n",
                "select first(a1) r1, last(a1) r2 from tab",
                "create table tab as (select cast(list(null,null,null) as string) a1 from long_sequence(3))",
                null,
                false,
                true
        );
    }

    @Test
    public void testFirstNullLastSomething() throws Exception {
        assertQuery(
                "r1\tr2\n" +
                        "\tsomething\n",
                "select first(a1) r1, last(a1) r2 from tab",
                "create table tab as (select cast(list(null,'else','something') as string) a1 from long_sequence(3))",
                null,
                false,
                true
        );
    }

    @Test
    public void testFirstSomethingLastNull() throws Exception {
        assertQuery(
                "r1\tr2\n" +
                        "something\t\n",
                "select first(a1) r1, last(a1) r2 from tab",
                "create table tab as (select cast(list('something','else',null) as string) a1 from long_sequence(3))",
                null,
                false,
                true
        );
    }

    @Test
    public void testFunctionArgument() throws Exception {
        assertQuery(
                "r1\tr2\tr3\tr4\n" +
                        "foobar\tbarbar\tfoobar\tbarbar\n",
                "select first(concat(a1,a2)) r1, last(concat(a1,a2)) r2, first_not_null(concat(a1,a2)) r3, last_not_null(concat(a1,a2)) r4 from tab",
                "create table tab as (select rnd_str('foo','bar') a1, rnd_str('bar','baz') a2 from long_sequence(10))",
                null,
                false,
                true
        );
    }

    @Test
    public void testFunctionArgumentAllNulls() throws Exception {
        assertQuery(
                "r1\tr2\tr3\tr4\n" +
                        "\t\t\t\n",
                "select first(concat(a1,a2)) r1, last(concat(a1,a2)) r2, first_not_null(concat(a1,a2)) r3, last_not_null(concat(a1,a2)) r4 from tab",
                "create table tab as (select rnd_str(null) a1, rnd_str(null) a2 from long_sequence(10))",
                null,
                false,
                true
        );
    }

    @Test
    public void testFunctionArgumentSomeNulls() throws Exception {
        assertQuery(
                "r1\tr2\tr3\tr4\n" +
                        "foobar\tfoobaz\tfoobar\tfoobaz\n",
                "select first(concat(a1,a2)) r1, last(concat(a1,a2)) r2, first_not_null(concat(a1,a2)) r3, last_not_null(concat(a1,a2)) r4 from tab",
                "create table tab as (select rnd_str('foo','bar',null) a1, rnd_str('bar','baz',null) a2 from long_sequence(10))",
                null,
                false,
                true
        );
    }

    @Test
    public void testGroupByOverUnion() throws Exception {
        assertQuery(
                "first\tlast\tfirst_nn\tlast_nn\n" +
                        "TJWCPSWHYR\t10\tTJWCPSWHYR\t10\n",
                "select first(s) first, last(s) last, first_not_null(s) first_nn, last_not_null(s) last_nn " +
                        "from (x union select x::string s, x::timestamp ts from long_sequence(10))",
                "create table x as (" +
                        "select * from (" +
                        "   select " +
                        "       rnd_str(10, 10, 0) s, " +
                        "       timestamp_sequence(0, 100000) ts " +
                        "   from long_sequence(10)" +
                        ") timestamp(ts))",
                null,
                false,
                true
        );
    }

    @Test
    public void testGroupKeyedFirstLastAllNulls() throws Exception {
        assertQuery(
                "a\tfirst\tlast\tfirst_not_null\tlast_not_null\n" +
                        "a\t\t\t\t\n" +
                        "b\t\t\t\t\n" +
                        "c\t\t\t\t\n",
                "select a, first(s), last(s), first_not_null(s), last_not_null(s) from x order by a",
                "create table x as (" +
                        "select * from (" +
                        "   select " +
                        "       rnd_symbol('a','b','c') a," +
                        "       null::string s, " +
                        "       timestamp_sequence(0, 100000) ts " +
                        "   from long_sequence(10)" +
                        ") timestamp(ts))",
                null,
                true,
                true
        );
    }

    @Test
    public void testGroupKeyedManyRows() throws Exception {
        assertQuery(
                "sum\n" +
                        "9877\n",
                "select sum(length(first) + length(last) + length(first_nn) + length(last_nn)) " +
                        "from " +
                        "( " +
                        "   select a, first(s) first, last(s) last, first_not_null(s) first_nn, last_not_null(s) last_nn " +
                        "   from x " +
                        ")",
                "create table x as (" +
                        "select * from (" +
                        "   select " +
                        "       rnd_symbol(300,10,10,0) a," +
                        "       rnd_str(400, 10, 10, 3) s, " +
                        "       timestamp_sequence(0, 100000) ts " +
                        "   from long_sequence(3000)" +
                        ") timestamp(ts))",
                null,
                false,
                true
        );
    }

    @Test
    public void testKeyedFirstLast1() throws Exception {
        assertMemoryLeak(() -> {
            execute("create table test (ts timestamp, device symbol, valueStr String, valueDb Double) timestamp(ts) partition by day");
            execute("insert into test (ts, device, valueStr, valueDb) VALUES \n" +
                    "        ('2023-12-18T18:00:00', 'A', null, null)," +
                    "        ('2023-12-18T18:00:00', 'B', null, null)," +
                    "        ('2023-12-18T18:00:00', 'A', 'hot_1', 150)," +
                    "        ('2023-12-18T18:00:00', 'B', 'cold_1', 3)," +
                    "        ('2023-12-18T18:00:00', 'A', null, null)," +
                    "        ('2023-12-18T18:00:00', 'B', null, null)," +
                    "        ('2023-12-18T18:00:00', 'A', 'hot_2', 151)," +
                    "        ('2023-12-18T18:00:00', 'B', 'cold_2', 4)," +
                    "        ('2023-12-18T18:00:00', 'A', null, null)," +
                    "        ('2023-12-18T18:00:00', 'B', null, null)");

            String query = "select ts, device, " +
                    "first(valueStr) first_str, " +
                    "first(valueDb) first_value, " +
                    "first_not_null(valueStr) first_nn_str, " +
                    "first_not_null(valueDb) first_nn_value, " +
                    "last(valueStr) last_str, " +
                    "last(valueDb) last_value, " +
                    "last_not_null(valueStr) last_nn_str, " +
                    "last_not_null(valueDb) last_nn_value " +
                    "from test";

            String expected = "ts\tdevice\tfirst_str\tfirst_value\tfirst_nn_str\tfirst_nn_value\tlast_str\tlast_value\tlast_nn_str\tlast_nn_value\n" +
                    "2023-12-18T18:00:00.000000Z\tA\t\tnull\thot_1\t150.0\t\tnull\thot_2\t151.0\n" +
                    "2023-12-18T18:00:00.000000Z\tB\t\tnull\tcold_1\t3.0\t\tnull\tcold_2\t4.0\n";

            assertSql(expected, query + " order by ts, device");
            assertSql(expected, query + " sample by 1h fill(prev) order by ts, device");
        });
    }

    @Test
    public void testKeyedFirstLast2() throws Exception {
        assertMemoryLeak(() -> {
            execute("create table test (ts timestamp, device symbol, valueStr String, valueDb Double) timestamp(ts) partition by day");
            execute("insert into test (ts, device, valueStr, valueDb) VALUES \n" +
                    "        ('2023-12-18T18:00:00', 'A', 'hot_1', 150)," +
                    "        ('2023-12-18T18:00:00', 'B', 'cold_1', 3)," +
                    "        ('2023-12-18T18:00:00', 'A', null, null)," +
                    "        ('2023-12-18T18:00:00', 'B', null, null)," +
                    "        ('2023-12-18T18:00:00', 'A', 'hot_2', 151)," +
                    "        ('2023-12-18T18:00:00', 'B', 'cold_2', 4)," +
                    "        ('2023-12-18T18:00:00', 'A', null, null)," +
                    "        ('2023-12-18T18:00:00', 'B', null, null)," +
                    "        ('2023-12-18T18:00:00', 'A', 'hot_3', 152)," +
                    "        ('2023-12-18T18:00:00', 'B', 'cold_3', 5)");

            String query = "select ts, device, " +
                    "first(valueStr) first_str, " +
                    "first(valueDb) first_value, " +
                    "first_not_null(valueStr) first_nn_str, " +
                    "first_not_null(valueDb) first_nn_value, " +
                    "last(valueStr) last_str, " +
                    "last(valueDb) last_value, " +
                    "last_not_null(valueStr) last_nn_str, " +
                    "last_not_null(valueDb) last_nn_value " +
                    "from test";

            String expected = "ts\tdevice\tfirst_str\tfirst_value\tfirst_nn_str\tfirst_nn_value\tlast_str\tlast_value\tlast_nn_str\tlast_nn_value\n" +
                    "2023-12-18T18:00:00.000000Z\tA\thot_1\t150.0\thot_1\t150.0\thot_3\t152.0\thot_3\t152.0\n" +
                    "2023-12-18T18:00:00.000000Z\tB\tcold_1\t3.0\tcold_1\t3.0\tcold_3\t5.0\tcold_3\t5.0\n";

            assertSql(expected, query + " order by ts, device");
            assertSql(expected, query + " sample by 1h fill(prev) order by ts, device");
        });
    }

    @Test
    public void testVarcharColoredPointerDoesNotLeak() throws Exception {
        assertMemoryLeak(() -> {
            execute("create table test (ts timestamp, vch varchar) timestamp(ts) partition by day");
            execute("insert into test (ts, vch) VALUES ('2023-12-17T18:00:00', 'first')");
            execute("insert into test (ts, vch) VALUES ('2023-12-18T18:00:00', 'last')");

            String query = "select first(vch) first_str, " +
                    "last(vch) last_str, " +
                    "from test";

            try (RecordCursorFactory cursorFactory = engine.select(query, sqlExecutionContext);
                 RecordCursor cursor = cursorFactory.getCursor(sqlExecutionContext)) {
                RecordMetadata metadata = cursorFactory.getMetadata();
                int firstStrColIdx = metadata.getColumnIndex("first_str");
                int lastStrColIdx = metadata.getColumnIndex("last_str");

                Record record = cursor.getRecord();
                Assert.assertTrue(cursor.hasNext());

                Utf8Sequence first = record.getVarcharA(firstStrColIdx);
                Utf8Sequence last = record.getVarcharA(lastStrColIdx);
                Assert.assertNotNull(first);
                Assert.assertNotNull(last);

                TestUtils.assertEquals("first", first);
                TestUtils.assertEquals("last", last);

                long firstPtr = first.ptr();
                long lastPtr = last.ptr();

                Assert.assertTrue(firstPtr > 0); // the highest bit is not set = the pointer is not colored -> the color is not leaking
                Assert.assertTrue(lastPtr > 0);

                DirectUtf8String flyweight = new DirectUtf8String();
                flyweight.of(firstPtr, firstPtr + first.size(), first.isAscii());
                TestUtils.equals("first", flyweight.asAsciiCharSequence());

                flyweight.of(lastPtr, lastPtr + last.size(), last.isAscii());
                TestUtils.equals("last", flyweight.asAsciiCharSequence());
            }
        });
    }
}
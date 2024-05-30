package net.ripe.rpki.rest.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.format.ISODateTimeFormat.dateTimeParser;
import static org.junit.Assert.assertEquals;

public class ObjectMapperProviderTest {

    ObjectMapper objectMapper = new ObjectMapperProvider().getContext(ObjectMapper.class);

    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(dateTimeParser().parseMillis("2014-03-15T14:55:00Z"));
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldSerializeDatetime() throws Exception {
        String serialized = objectMapper.writeValueAsString(new DateTimeWrapper());
        assertEquals("{\"dateTime\":\"2014-03-15T14:55:00.000Z\"}", serialized);
    }

    @Test
    public void shouldDeserializeDateTimeFromUTC() throws Exception {
        String serialized = "{\"dateTime\":\"2014-05-16T21:25:12.345Z\"}";
        DateTimeWrapper deserialized = objectMapper.readValue(serialized, DateTimeWrapper.class);
        DateTime dateTime = deserialized.getDateTime();

        assertEquals(2014, dateTime.getYearOfEra());
        assertEquals(5, dateTime.getMonthOfYear());
        assertEquals(16, dateTime.getDayOfMonth());
        assertEquals(21, dateTime.getHourOfDay());
        assertEquals(25, dateTime.getMinuteOfHour());
        assertEquals(12, dateTime.getSecondOfMinute());
        assertEquals(345, dateTime.getMillisOfSecond());
        assertEquals(UTC, dateTime.getZone());
    }

    @Test
    public void shouldDeserializeDateTimesFromOtherTimeZones() throws Exception {
        String serialized = "{\"dateTime\":\"2014-06-06T10:34:10.381+02:00\"}";
        DateTimeWrapper deserialized = objectMapper.readValue(serialized, DateTimeWrapper.class);
        DateTime dateTime = deserialized.getDateTime();

        assertEquals(8, dateTime.getHourOfDay());
        assertEquals(UTC, dateTime.getZone());
    }


    @Test
    public void shouldDeserializeDateTimesWithoutTimeZone() throws Exception {
        String serialized = "{\"dateTime\":\"2014-06-06T10:34\"}";
        DateTimeWrapper deserialized = objectMapper.readValue(serialized, DateTimeWrapper.class);
        DateTime dateTime = deserialized.getDateTime();

        assertEquals(10, dateTime.getHourOfDay());
        assertEquals(UTC, dateTime.getZone());
    }

    @Test
    public void shouldDeserializeDates() throws Exception {
        String serialized = "{\"dateTime\":\"2014-05-16\"}";
        DateTimeWrapper deserialized = objectMapper.readValue(serialized, DateTimeWrapper.class);
        DateTime dateTime = deserialized.getDateTime();

        assertEquals(2014, dateTime.getYearOfEra());
        assertEquals(5, dateTime.getMonthOfYear());
        assertEquals(16, dateTime.getDayOfMonth());
        assertEquals(0, dateTime.getHourOfDay());
        assertEquals(0, dateTime.getMinuteOfHour());
        assertEquals(0, dateTime.getSecondOfMinute());
        assertEquals(0, dateTime.getMillisOfSecond());
        assertEquals(UTC, dateTime.getZone());
    }

    static class DateTimeWrapper {
        private DateTime dateTime = new DateTime(DateTimeZone.UTC);

        public void setDateTime(DateTime dateTime) {
            this.dateTime = dateTime;
        }

        public DateTime getDateTime() {
            return dateTime;
        }
    }
}

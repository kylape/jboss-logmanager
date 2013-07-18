/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.logmanager.handlers;

import java.util.Calendar;
import java.util.logging.Level;

import junit.framework.Assert;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.handlers.SyslogHandler.Facility;
import org.jboss.logmanager.handlers.SyslogHandler.SyslogType;
import org.testng.annotations.Test;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.concurrent.CountDownLatch;
import java.net.InetAddress;
import java.io.IOException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Test
public class SyslogHandlerTests {

    private static final String MSG = "This is a test message";
    private static final String MULTIBYTE_MSG = "Thïs ïs a test message wïth a multïbyte char for every ï.";

    public void testRFC5424Format() throws Exception {
        final Calendar cal = getCalendar();
        String formattedMessage = SyslogType.RFC5424.format(createRecord(cal, MSG), Facility.USER_LEVEL, "test", "1234", "java");
        String expectedMessage = "<14>1 2012-01-09T04:39:22.000" + calculateTimeZone(cal) + " test java 1234 - - " + MSG;
        Assert.assertEquals(expectedMessage, formattedMessage);

        formattedMessage = SyslogType.RFC5424.format(createRecord(cal, MSG), Facility.USER_LEVEL, null, "1234", null);
        expectedMessage = "<14>1 2012-01-09T04:39:22.000" + calculateTimeZone(cal) + " - - 1234 - - " + MSG;
        Assert.assertEquals(expectedMessage, formattedMessage);

        cal.set(Calendar.DAY_OF_MONTH, 31);
        formattedMessage = SyslogType.RFC5424.format(createRecord(cal, MSG), Facility.USER_LEVEL, "test", "1234", "java");
        expectedMessage = "<14>1 2012-01-31T04:39:22.000" + calculateTimeZone(cal) + " test java 1234 - - " + MSG;
        Assert.assertEquals(expectedMessage, formattedMessage);
    }

    public void testRFC31644Format() throws Exception {
        final Calendar cal = getCalendar();
        String formattedMessage = SyslogType.RFC3164.format(createRecord(cal, MSG), Facility.USER_LEVEL, "test", "1234", "java");
        String expectedMessage = "<14>Jan  9 04:39:22 test java[1234]: " + MSG;
        Assert.assertEquals(expectedMessage, formattedMessage);

        cal.set(Calendar.DAY_OF_MONTH, 31);
        formattedMessage = SyslogType.RFC3164.format(createRecord(cal, MSG), Facility.USER_LEVEL, "test", "1234", null);
        expectedMessage = "<14>Jan 31 04:39:22 test [1234]: " + MSG;
        Assert.assertEquals(expectedMessage, formattedMessage);
    }

    public void testMultibyteChar() throws Exception {
        final Calendar cal = getCalendar();
        final ExtLogRecord logRecord = createRecord(cal, MULTIBYTE_MSG);
        final String formattedMessage = SyslogType.RFC3164.format(logRecord, Facility.USER_LEVEL, "test", "1234", "java");
        final int SYSLOG_PORT = 44483;
        final String SYSLOG_HOST = InetAddress.getLocalHost().getHostName();
        final SyslogHandler handler = new SyslogHandler(SYSLOG_HOST, SYSLOG_PORT);
        handler.setSyslogType(SyslogType.RFC3164);
        final StringBuffer buffer = new StringBuffer();
        final CountDownLatch publishLatch = new CountDownLatch(1);
        final CountDownLatch bufferLatch  = new CountDownLatch(1);
        new Thread(new Runnable() {
            public void run() {
                try {
                    DatagramSocket dgSocket = new DatagramSocket(SYSLOG_PORT, InetAddress.getLocalHost());
                    DatagramPacket dgPacket = new DatagramPacket(new byte[1024], 0, 1024);
                    publishLatch.countDown();
                    dgSocket.receive(dgPacket);
                    String dgMessage = new String(dgPacket.getData(), dgPacket.getOffset(), dgPacket.getLength());
                    buffer.append(dgMessage);
                    bufferLatch.countDown();
                } catch (IOException ioe) {
                    // nothing 
                }
            }
        }).start();
        try {
            publishLatch.await();
            handler.publish(logRecord);
            bufferLatch.await();
        } catch (InterruptedException ie) {
            // nothing
        }
        String expectedMessage = "<14>Jan  9 04:39:22 test java[1234]: " + MULTIBYTE_MSG;
        String expectedMessageContent = expectedMessage.substring(expectedMessage.indexOf(": "));
        String actualMessageContent   = buffer.toString();
        actualMessageContent = actualMessageContent.substring(actualMessageContent.indexOf(": "));
        Assert.assertEquals(expectedMessageContent, actualMessageContent);
    }

    private static ExtLogRecord createRecord(final Calendar cal, final String message) {
        final String loggerName = SyslogHandlerTests.class.getName();
        final ExtLogRecord record = new ExtLogRecord(Level.INFO, message, loggerName);
        record.setMillis(cal.getTimeInMillis());
        return record;
    }

    private static Calendar getCalendar() {
        final Calendar cal = Calendar.getInstance();
        cal.set(2012, Calendar.JANUARY, 9, 4, 39, 22);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static String calculateTimeZone(final Calendar cal) {
        final int tz = cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET);
        final StringBuilder buffer = new StringBuilder();
        if (tz == 0) {
            buffer.append("+00:00");
        } else {
            int tzMinutes = tz / 60000; // milliseconds to minutes
            if (tzMinutes < 0) {
                tzMinutes = -tzMinutes;
                buffer.append('-');
            } else {
                buffer.append('+');
            }
            final int tzHour = tzMinutes / 60; // minutes to hours
            tzMinutes -= tzHour * 60; // subtract hours from minutes in minutes
            if (tzHour < 10) {
                buffer.append(0);
            }
            buffer.append(tzHour).append(':');
            if (tzMinutes < 10) {
                buffer.append(0);
            }
            buffer.append(tzMinutes);
        }
        return buffer.toString();
    }
}

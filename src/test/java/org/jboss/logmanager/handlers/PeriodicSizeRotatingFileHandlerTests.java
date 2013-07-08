/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.Assert;
import org.junit.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PeriodicSizeRotatingFileHandlerTests extends AbstractHandlerTest {
    private final static String FILENAME = "rotating-file-handler.log";

    private final File logFile = new File(BASE_LOG_DIR, FILENAME);

    private static final List<Integer> supportedPeriods = new ArrayList<Integer>();
    private static final Map<Integer, SimpleDateFormat> periodFormatMap = 
        new HashMap<Integer, SimpleDateFormat>();

    static {
        supportedPeriods.add(Calendar.YEAR);
        supportedPeriods.add(Calendar.MONTH);
        supportedPeriods.add(Calendar.WEEK_OF_YEAR);
        supportedPeriods.add(Calendar.DAY_OF_MONTH);
        supportedPeriods.add(Calendar.AM_PM);
        supportedPeriods.add(Calendar.HOUR);
        supportedPeriods.add(Calendar.MINUTE);

        //There are additional formats that could be tested here
        periodFormatMap.put(Calendar.YEAR, new SimpleDateFormat("yyyy"));
        periodFormatMap.put(Calendar.MONTH, new SimpleDateFormat("yyyy-MM"));
        periodFormatMap.put(Calendar.WEEK_OF_YEAR, new SimpleDateFormat("yyyy-ww"));
        periodFormatMap.put(Calendar.DAY_OF_MONTH, new SimpleDateFormat("yyyy-MM-dd"));
        periodFormatMap.put(Calendar.AM_PM, new SimpleDateFormat("yyyy-MM-dda"));
        periodFormatMap.put(Calendar.HOUR, new SimpleDateFormat("yyyy-MM-dd-HH"));
        periodFormatMap.put(Calendar.MINUTE, new SimpleDateFormat("yyyy-MM-dd-HH-mm"));
    }

    @Test
    public void testSizeRotate() throws Exception {
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        final Calendar cal = Calendar.getInstance();
        final String extension = "." + fmt.format(cal.getTimeInMillis());

        final PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(1024L);
        handler.setMaxBackupIndex(2);
        handler.setSuffix("." + fmt.toPattern());
        handler.setFile(logFile);

        // Allow a few rotates
        for (int i = 0; i < 100; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // We should end up with 3 files, 2 rotated and the default log
        final File file1 = new File(BASE_LOG_DIR, FILENAME + extension + ".1");
        final File file2 = new File(BASE_LOG_DIR, FILENAME + extension + ".2");
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(file1.exists());
        Assert.assertTrue(file2.exists());

        // Clean up files
        file1.delete();
        file2.delete();
    }

    @Test
    public void testBootRotate() throws Exception {
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        final Calendar cal = Calendar.getInstance();
        final String extension = "." + fmt.format(cal.getTimeInMillis());

        PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        // Enough to not rotate
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
        handler.setSuffix("." + fmt.toPattern());
        handler.setRotateOnBoot(true);
        handler.setFile(logFile);
        final File rotatedFile = new File(BASE_LOG_DIR, FILENAME + extension + ".1");

        // The rotated file should not exist
        Assert.assertFalse("Rotated file should not exist", rotatedFile.exists());

        // Log a few records
        for (int i = 0; i < 5; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        // Close the handler and create a new one
        handler.close();
        final long size = logFile.length();
        handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        handler.setRotateSize(5000L);
        handler.setMaxBackupIndex(1);
        handler.setSuffix("." + fmt.toPattern());
        handler.setRotateOnBoot(true);
        handler.setFile(logFile);

        // The rotated file should exist
        Assert.assertTrue("Rotated file should exist", rotatedFile.exists());

        // Rotated file size should match the size of the previous file
        Assert.assertEquals(size, rotatedFile.length());

        // Log a few records
        for (int i = 0; i < 10; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        handler.close();

        // File should have been rotated
        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(rotatedFile.exists());

        // Neither file should be empty
        Assert.assertTrue(logFile.length() > 0L);
        Assert.assertTrue(rotatedFile.length() > 0L);

        // Clean up files
        rotatedFile.delete();
    }

    @Test
    public void testPeriodicAndSizeRotate() throws Exception {
        for (int i=0; i < supportedPeriods.size(); i++) {
            //To cut down on unnecessary testing, let's only test
            //the periods +/- two from this period
            int j = i-2;
            if (j < 0) j = 0;

            int handlerPeriod = supportedPeriods.get(i);
            for (; j <= i+2; j++) {
                if (j >= supportedPeriods.size()) break;
                int logMessagePeriod = supportedPeriods.get(j);
                System.out.println("Handler: " + periodFormatMap.get(handlerPeriod).toPattern() + 
                                   " ; Message: " + periodFormatMap.get(logMessagePeriod).toPattern());
                testPeriodicAndSizeRotate0(handlerPeriod, logMessagePeriod, true);
                testPeriodicAndSizeRotate0(handlerPeriod, logMessagePeriod, false);
            }
        }
    }

    private void testPeriodicAndSizeRotate0(int handlerPeriod, int logMessagePeriod, boolean testSize) throws Exception {
        int logCount = 1;
        if (testSize) {
            logCount = 100;
        }
        final long rotateSize = 1024L;
        final SimpleDateFormat fmt = periodFormatMap.get(handlerPeriod);
        final Calendar cal = Calendar.getInstance();
        String extension = "." + fmt.format(cal.getTimeInMillis());

        PeriodicSizeRotatingFileHandler handler = new PeriodicSizeRotatingFileHandler();
        configureHandlerDefaults(handler);
        // Enough to not rotate
        handler.setRotateSize(rotateSize);
        handler.setMaxBackupIndex(2);
        handler.setSuffix("." + fmt.toPattern());
        handler.setFile(logFile);

        // Write a record
        for (int i = 0; i < logCount; i++) {
            handler.publish(createLogRecord("Test message: %d", i));
        }

        File rotatedFile1, rotatedFile2;
        if (testSize) {
            rotatedFile1 = new File(BASE_LOG_DIR, FILENAME + extension + ".1");
            rotatedFile2 = new File(BASE_LOG_DIR, FILENAME + extension + ".2");

            // File should have been rotated
            String message = "Log should have rotated, but it did not\n";
            Assert.assertTrue(logFile.exists());
            Assert.assertTrue(message + rotatedFile1.getPath(), rotatedFile1.exists());
            Assert.assertTrue(message + rotatedFile2.getPath(), rotatedFile2.exists());
        }

        // Increase the calender to force a rotation
        cal.add(logMessagePeriod, 1);

        // Write a new record which should result in a rotation
        for (int i = 0; i < logCount; i++) {
            ExtLogRecord record = createLogRecord("Test message: %d", i);
            record.setMillis(cal.getTimeInMillis());
            handler.publish(record);
        }

        handler.close();

        if (testSize) {
            // The extension name will be the new period since the size rotation
            // has happened since the date rotation
            extension = "." + fmt.format(cal.getTimeInMillis());
            rotatedFile1 = new File(BASE_LOG_DIR, FILENAME + extension + ".1");
            rotatedFile2 = new File(BASE_LOG_DIR, FILENAME + extension + ".2");
        } else {
            // The extension name will still be the old period since no size rotation
            // has happened to bump up the new period
            rotatedFile1 = new File(BASE_LOG_DIR, FILENAME + extension);
            rotatedFile2 = new File(BASE_LOG_DIR, FILENAME + extension);
        }

        String patterns = "Handler: " + periodFormatMap.get(handlerPeriod).toPattern() + " ; " + 
                           "Message: " + periodFormatMap.get(logMessagePeriod).toPattern() + " ; " +
                           "testSize=" + testSize;

        Assert.assertTrue(logFile.exists());
        Assert.assertTrue(logFile.length() > 0L);

        // If the time period added to the log message is greater than the time period specified
        // for file rotation, then we should expect the log to have rotated
        // The bigger the time period, the smaller the int
        try {
            if (logMessagePeriod <= handlerPeriod || testSize) {
                String message = "Expected log rotation, but it didn't happen\n";
                Assert.assertTrue(message + patterns + "\n" + rotatedFile1.getPath(), rotatedFile1.exists());
                Assert.assertTrue(message + patterns, rotatedFile2.exists());
                Assert.assertTrue(rotatedFile1.length() > 0L);
                Assert.assertTrue(rotatedFile2.length() > 0L);
            } else {
                String message = "Expected NO log rotation, but it happened anyways\n";
                Assert.assertFalse(message + patterns + "\n" + rotatedFile1.getPath(), rotatedFile1.exists());
                Assert.assertFalse(message + patterns, rotatedFile2.exists());
                Assert.assertFalse(rotatedFile1.length() > 0L);
                Assert.assertFalse(rotatedFile2.length() > 0L);
            }
        } finally {
            logFile.delete();
            rotatedFile1.delete();
            rotatedFile2.delete();
        }
    }
}

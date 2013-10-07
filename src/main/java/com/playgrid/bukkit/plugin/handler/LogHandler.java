package com.playgrid.bukkit.plugin.handler;

import java.util.ArrayList;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class LogHandler extends Handler {
	ArrayList<LogRecord> log = new ArrayList<LogRecord>();

	@Override
	public void publish(LogRecord record) {
		log.add(record);
	}

	@Override
	public void flush() {
		log.clear();
	}

	@Override
	public void close() throws SecurityException {
	}
	
	public String toString() {
		String string = "";
		for(LogRecord r : log) {
			DateTime jodaTime = new DateTime(r.getMillis(),DateTimeZone.forTimeZone(TimeZone.getDefault()));
	        DateTimeFormatter parser = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
	        String time = parser.print(jodaTime);
			string += "[" + time + "] " + r.getMessage() + "\n";
		}
		return string;
	}
}

package com.elmocity.ss.ib;

// Static import to fetch all the constant strings for shorthand
import static com.elmocity.ss.ib.IBMarker.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IBMarkerList
{
	static final Logger logger = LoggerFactory.getLogger(IBMarkerList.class);

	
	private ArrayList<IBMarker> markers = new ArrayList<>();

	public void add(String message)
	{
		markers.add(new IBMarker(message));	
	}

	public void debugDump()
	{
		logger.debug("marker dump:");
		for (int i = 0; i < markers.size(); i++) {
			logger.debug("marker {}", markers.get(i).debugString());
		}
	}
	
	public void analyzeMarkers()
	{
		// PART 1: show overall OER calculations

		// These are the 3 messages we want to count up
		String[] messages = { OER_CREATE_ORDER, OER_MODIFY_ORDER, OER_CANCEL_ORDER };
		
		logger.info("OER TOTAL COUNT");
		for (int type = 0; type < messages.length; type++) {
			int count = 0;
			for (int i = 0; i < markers.size(); i++) {
				IBMarker marker = markers.get(i);
				if (marker.name.equals(messages[type])) {
					count++;
				}
			}
			
//			log("OER " + messages[type] + " count = " + count);
			logger.info("OER {} {}", messages[type], count);
		}
		
		// PART 2: show number of requests each second, for the first few seconds, since thats the most likely time we might hit the limit.
		final LocalTime startTime = LocalTime.of(9, 30, 0);		// Opening Bell, TODO we should count a few seconds BEFORE the bell too
		final int secsToAnalyze = 20;
		
		// Each BIN is a one sec window, from 0.000 to 0.999 seconds.   Watch the edge comparisons so that a single marker is not
		// counted twice (in two different bins), if it happens to occur exactly on a sec boundary.
		
		for (int sec = 0; sec < secsToAnalyze; sec++) {
			LocalTime binStart = startTime.plusSeconds(sec);		// Leading edge of the bin is "inclusive", we want to count X.000 in this bin
			LocalTime binEnd = startTime.plusSeconds(sec + 1);	// Trailing edge of the bin is "exclusive", we don't count (X+1).000 in this bin
			
			int count = 0;
			for (int i = 0; i < markers.size(); i++) {
				IBMarker marker = markers.get(i);
				if (marker.name.equals(OER_MODIFY_ORDER)) {
					// Hit the leading edge so count it
					if (marker.localTime.equals(binStart)) {
						count++;
					}
					// Hit inside the bin, note that isBefore() won't include the trading edge
					else if (marker.localTime.isAfter(binStart) && marker.localTime.isBefore(binEnd)) {
						count++;
					}
				}
			}
//			log("  bin " + binStart.toString() + " count = " + count);
			logger.info("  bin {} {}", binStart.toString(), count);
		}
	}
	
//	public void dump(JobLog jobLog)
//	{
//		StringBuilder sb = new StringBuilder();
//		
//		for (int i = 0; i < markers.size(); i++) {
//			sb.append("Markers " + markers.get(i).debugString() + "\n");
//		}
//
//		// Always send to stdout, but also send to jobLog if caller provided one
//		String result = sb.toString();
//		if (jobLog != null) {
//			jobLog.log(result);
//		}
////		SS.log(sb.toString());
//	}
}

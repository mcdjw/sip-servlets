package com.oracle.custom.sip.talkbac.server;

import org.apache.log4j.Logger;

public class CallEventLogger
{
	private static Logger log = null;
	
	public static void initialize()
	{
		log = Logger.getLogger("EventLogger");
	}
	
	public static void logEvent(String requestId, String message)
	{
		log.info(requestId + ":[" + message + "]");
	}
}

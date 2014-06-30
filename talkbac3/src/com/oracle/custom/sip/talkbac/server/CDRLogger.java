package com.oracle.custom.sip.talkbac.server;

import javax.servlet.sip.SipApplicationSession;

import org.apache.log4j.Logger;

public class CDRLogger 
{
	private static Logger log = null;
	
	public static void initialize()
	{
		log = Logger.getLogger("CDRLogger");
	}
	
	public static void logCDR(SipApplicationSession session, Channel channel)
	{
		log.info(session.getAttribute(TalkbacUtil.USER_ID) + "," + session.getAttribute(TalkbacUtil.ORIGIN_ADDR) + "," + 
				session.getAttribute(TalkbacUtil.DEST_ADDR) + "," + session.getAttribute(TalkbacUtil.START_TIME) + "," + 
				session.getAttribute(TalkbacUtil.END_TIME) + "," + channel.getVal());
	}
}

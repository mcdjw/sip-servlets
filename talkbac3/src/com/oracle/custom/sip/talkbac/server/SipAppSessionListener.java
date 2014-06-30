package com.oracle.custom.sip.talkbac.server;

import javax.servlet.sip.SipApplicationSessionEvent;
import javax.servlet.sip.SipApplicationSessionListener;

import org.apache.log4j.Logger;

public class SipAppSessionListener implements SipApplicationSessionListener 
{

	static Logger logger = Logger.getLogger(SipAppSessionListener.class);

	@Override
	public void sessionCreated(SipApplicationSessionEvent event) 
	{
		logger.info("app session created with id" + event.getApplicationSession().getId());
	}

	@Override
	public void sessionDestroyed(SipApplicationSessionEvent event) 
	{
		logger.info("app session destroyed with id" + event.getApplicationSession().getId());
		Object reqId = event.getApplicationSession().getAttribute(TalkbacUtil.REQUEST_ID);
		if(reqId != null)
		{
			CallEventLogger.logEvent((String)reqId, "Sip Application session destroyed");
		}
	}

	@Override
	public void sessionExpired(SipApplicationSessionEvent event) 
	{
		//need to check if this is first time expired, if yes then disconnect will be sent to ASC and session lifetime should be extended to 
		//receive terminated events and write cdr.the session lifetime will be extended by 60 sec for this activity
		//if this is seconds expired , then let the session die. already finished disconnecting call, cdr log etc.
		logger.info("app session expired"
				+ " with id" + event.getApplicationSession().getId());
		Object reqId = event.getApplicationSession().getAttribute(TalkbacUtil.REQUEST_ID);
		if(reqId != null)
		{
			if(((Boolean)event.getApplicationSession().getAttribute(TalkbacUtil.IS_TIME_OUT_DISCONNECT_RAISED)).booleanValue())
			{
				//second time expired , do nothing, let session die
				CallEventLogger.logEvent((String)reqId, "Call disconnect procedure and clean up complete");
			}
			else
			{
				CallEventLogger.logEvent((String)reqId, "Sip Application session expired");
				TalkBACSipServlet.disconnectOnTimeout(event.getApplicationSession());
				event.getApplicationSession().setAttribute(TalkbacUtil.IS_TIME_OUT_DISCONNECT_RAISED, true);
				event.getApplicationSession().setExpires(1);//extend session lifetime
				logger.info("Session life time extended by 1 min");
			}
		}
	}

	@Override
	public void sessionReadyToInvalidate(SipApplicationSessionEvent event) 
	{
		logger.info("app session ready to invalidate with id" + event.getApplicationSession().getId());
		
		Object reqId = event.getApplicationSession().getAttribute(TalkbacUtil.REQUEST_ID);
		if(reqId != null)
		{
			CallEventLogger.logEvent((String)reqId, "Sip Application session ready to invalidate");
		}
	}

}


package com.oracle.custom.asc.callout;

import java.util.Date;
import java.util.Iterator;
import java.util.List;



import javax.jws.WebService;
import javax.servlet.sip.SipApplicationSession;
import javax.xml.ws.BindingType;
import org.apache.log4j.Logger;

import com.oracle.custom.sip.talkbac.server.CDRLogger;
import com.oracle.custom.sip.talkbac.server.CallEventLogger;
import com.oracle.custom.sip.talkbac.server.Channel;
import com.oracle.custom.sip.talkbac.server.EventCode;
import com.oracle.custom.sip.talkbac.server.EventType;
import com.oracle.custom.sip.talkbac.server.TalkBACSipServlet;
import com.oracle.custom.sip.talkbac.server.TalkbacUtil;


/**
 * This class was generated by the JAX-WS RI.
 * Oracle JAX-WS 2.1.5
 * Generated source version: 2.1
 * 
 */
@WebService(portName = "EventProcessingPort", serviceName = "EventProcessingService", targetNamespace = "http://www.acmepacket.com/asc/ws/mgmt", wsdlLocation = "/wsdls/AcmePacketASCManagement.wsdl", endpointInterface = "com.oracle.custom.asc.callout.EventProcessingPortType")
@BindingType("http://schemas.xmlsoap.org/wsdl/soap/http")
public class EventProcessingService_EventProcessingPortImpl
    implements EventProcessingPortType
{

	static Logger logger = Logger.getLogger(EventProcessingService_EventProcessingPortImpl.class);
	
    public EventProcessingService_EventProcessingPortImpl() {
    }

    /**
     * 
     *                 This operation is implemented by third parties.  This is a call out to allow a third party to process
     *                 events generated by the Net-Net Application Session Controller.
     *             
     * 
     * @param event
     */
    public void processEvent(List<TrapClassesType> event) 
    {
        logger.info("Got event from ASC");
        int eventInd = 0;
        int reason = 0;
        if(event.size() > 0)
        {
        	Iterator<TrapClassesType> trapIt = event.iterator();
        	while(trapIt.hasNext())
        	{
        		TrapClassesType trap = trapIt.next();
        		logger.info(trap.getObject().toString());
        		CallEventType eveType = null;
        		boolean handleEvent = false;
        		/****
        		if(trap.getObject() instanceof CallCreatedEventType)
        		{
        			logger.info("Receviced a call created event");
        			eventStr = "call created";
        			CallCreatedEventType eve = (CallCreatedEventType) trap.getObject();
        			eveType = eve.getCallEvent();
        			handleEvent = true;
        		}
        		**/
        		if(trap.getObject() instanceof CallConnectingEventType)
        		{
        			eventInd = 1;
        			logger.info("Receviced a call connecting event");
        			CallConnectingEventType eve = (CallConnectingEventType) trap.getObject();
        			eveType = eve.getCallEvent();
        			if(eveType.getRequestID() != null)
        			{
        				CallEventLogger.logEvent(eveType.getRequestID(), "Recevied call connecting event from ASC for handler id: " + eveType.getHandle());
        			}
        			handleEvent = true;
        		}
        		else if(trap.getObject() instanceof CallConnectedEventType)
        		{
        			eventInd = 2;
        			logger.info("Receviced a call connected event");
        			CallConnectedEventType eve = (CallConnectedEventType) trap.getObject();
        			eveType = eve.getCallEvent();
        			if(eveType.getRequestID() != null)
        			{
        				CallEventLogger.logEvent(eveType.getRequestID(), "Recevied call connected event from ASC for handler id: " + eveType.getHandle());
        			}
        			handleEvent = true;
        		}
        		else if(trap.getObject() instanceof CallTerminatedEventType)
        		{
        			eventInd = 3;
        			logger.info("Receviced a call terminated event");
        			CallTerminatedEventType eve = (CallTerminatedEventType) trap.getObject();
        			eveType = eve.getCallEvent();
        			reason = Long.valueOf(eve.getReason()).intValue();
					logger.info("Terminated reason" + reason);
        			handleEvent = true;
        			if(eveType.getRequestID() != null)
        			{
        				CallEventLogger.logEvent(eveType.getRequestID(), "Recevied call terminated event from ASC for handler id: " + eveType.getHandle());
        			}
        		}
        		if(handleEvent && eveType.getRequestID() != null)
        		{
        			String reqId = eveType.getRequestID();
        			long handlerId = eveType.getHandle();
	    			logger.info("RequestId: " + eveType.getRequestID());
	    			logger.info("Handle Id: " + eveType.getHandle());
	    			if(TalkBACSipServlet.util != null)
	    			{
		    			SipApplicationSession appSession = TalkBACSipServlet.util.getApplicationSessionByKey(reqId, false);
		    			if(appSession != null)
		    			{
		    				if(handlerId == ((Long)appSession.getAttribute(TalkbacUtil.ASC_IN_CALL_LEG_HANDLE)).longValue())
		    				{
		    					logger.info("Received event for caller");
		    					switch(eventInd)
		    					{
			    					case 1:
			    						TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.ORIGIN_CONNECTING, false);
			    						appSession.setAttribute(TalkbacUtil.IS_ORIGIN_CONNECTING, true);
			    						break;
			    					case 2:
			    						TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.ORIGIN_CONNECTED, false);
			    						appSession.setAttribute(TalkbacUtil.IS_ORIGIN_CONNECTED, true);
			    						//other party already connected, so record start time of call
			    						if(((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_DEST_CONNECTED)).booleanValue())
			    						{
				    						appSession.setAttribute(TalkbacUtil.START_TIME,new Date());
			    						}
			    						break;
			    					case 3:
		    							logger.info("Handling caller terminated event");
			    						switch(reason)
			    						{
			    							//following case will occur only in case of connecting caller and connecting with callee will never happen. so appsession should be invalidated
				    						case 486:
				    							TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.ORIGIN_BUSY, true);
				    							break;
				    						case 404:
				    						case 483:
				    							TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.ORIGIN_INAVLID, true);
				    							break;
				    						case 200:
				    							logger.info("got normal caller terminated event");
				    							try
				    							{
				    								//check if callee leg started and not terminated, if yes then wait for call terminated event.do not invalidate session
				    								boolean isCalleeConnecting = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_DEST_CONNECTING)).booleanValue();
				    								boolean isCalleeTerminated = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_DIST_TERMINATED)).booleanValue();
				    								boolean isCalleeConnected = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_DEST_CONNECTED)).booleanValue();
				    								boolean isCallerConnected = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_ORIGIN_CONNECTED)).booleanValue();
				    								appSession.setAttribute(TalkbacUtil.IS_ORIGIN_TERMINATED, true);
				    								logger.info("isCalleConnecting:" + isCalleeConnecting + ", isCalleeTerminated:" + isCalleeTerminated +
				    										", isCalleeConnected:" + isCalleeConnected + ", isCallerConncted:" + isCallerConnected);
				    								if(isCalleeConnected && isCallerConnected && !isCalleeTerminated)//if the call in ongoing record end time
				    								{
				    									logger.info("callee connected, caller connected and callee, caller terminated generating cdr");
				    									appSession.setAttribute(TalkbacUtil.END_TIME, new Date());
				    									logger.info("************Generating CDR*****************");
				    									CallEventLogger.logEvent(reqId, "Generating CDR");
				    									CDRLogger.logCDR(appSession, Channel.ASC);
				    								}
				    								if(!isCalleeConnecting || (isCalleeConnecting && isCalleeTerminated))
				    								{
				    									logger.info("callee does not exists or already disconnected, hence caller disconnect is final event");
				    									TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.ORIGIN_DISCONNECTED, true);
				    								}
				    								else //wait for callee termination event
				    								{
				    									logger.info("only caller disconnected, should wait for callee disconnet event");
				    									TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.ORIGIN_DISCONNECTED, false);
				    								}
				    							}
				    							catch(Exception e)
				    							{
				    								logger.error(e.getMessage());
				    							}
					    						break;
					    					default://any other error code likve 5xx etc
					    						TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.SERVER_ERROR, true);
				    							break;
			    						}
			    						break;
		    					}
		    				}
		    				else if(handlerId == ((Long)appSession.getAttribute(TalkbacUtil.ASC_OUT_CALL_LEG_HANDLE)).longValue())
		    				{
		    					logger.info("Received event for callee");
		    					switch(eventInd)
		    					{
			    					case 1:
			    						TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.DEST_CONNECTING, false);
			    						appSession.setAttribute(TalkbacUtil.IS_DEST_CONNECTING, true);
			    						break;
			    					case 2:
			    						TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.DEST_CONNECTED, false);
			    						appSession.setAttribute(TalkbacUtil.IS_DEST_CONNECTED, true);
			    						//other party already connected, so record start time of call
			    						if(((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_ORIGIN_CONNECTED)).booleanValue())
			    						{
				    						appSession.setAttribute(TalkbacUtil.START_TIME,new Date());
			    						}
			    						break;
			    					case 3:
			    						logger.info("Handling callee terminated event");
			    						switch(reason)
			    						{
				    						case 486:
				    							TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.DEST_BUSY, true);
				    							break;
				    						case 404:
				    						case 483:
				    							TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.DEST_INVALID, true);
				    							break;
				    						case 200:
				    							logger.info("got normal callee terminated event");
				    							try
				    							{
				    								//check if callee leg started and not terminated, if yes then wait for call terminated event.do not invalidate session
				    								boolean isCallerConnecting = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_ORIGIN_CONNECTING)).booleanValue();
				    								boolean isCallerTerminated = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_ORIGIN_TERMINATED)).booleanValue();
				    								boolean isCalleeConnected = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_DEST_CONNECTED)).booleanValue();
				    								boolean isCallerConnected = ((java.lang.Boolean)appSession.getAttribute(TalkbacUtil.IS_ORIGIN_CONNECTED)).booleanValue();
				    								appSession.setAttribute(TalkbacUtil.IS_DIST_TERMINATED, true);
				    								logger.info("isCallerConnecting:" + isCallerConnecting + ", isCallerTerminated:" + isCallerTerminated +
				    										", isCalleeConnected:" + isCalleeConnected + ", isCallerConncted:" + isCallerConnected);
				    								if(isCalleeConnected && isCallerConnected && !isCallerTerminated)//if the call in ongoing record end time
				    								{
				    									logger.info("callee connected, caller connected and callee, caller terminated generating cdr");
				    									logger.info("************Generating CDR*****************");
				    									CallEventLogger.logEvent(reqId, "Generating CDR");
				    									appSession.setAttribute(TalkbacUtil.END_TIME, new Date());
				    									CDRLogger.logCDR(appSession, Channel.ASC);
				    								}
				    								if(!isCallerConnecting || (isCallerConnecting && isCallerTerminated))
				    								{
				    									logger.info("caller does not exists or already disconnected, hence callee disconnect is final event");
				    									TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.DEST_DISCONNECTED, true);
				    								}
				    								else //wait for callee termination event
				    								{
				    									logger.info("only callee disconnected, should wait for caller disconnet event");
				    									TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.DEST_DISCONNECTED, false);
				    								}
				    							}
				    							catch(Exception e)
				    							{
				    								logger.error(e);
				    							}
					    						break;
					    					default://other error codes line 5xx
				    							TalkBACSipServlet.processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.SERVER_ERROR, true);
				    							break;

			    						}
			    						break;
		    					}
		    				}
		    			}
		    			else 
		    			{
		    				logger.info("Can not find sip app session for requirest id " + reqId);
		    				CallEventLogger.logEvent(reqId, "Sip Application Session does not exist for request id");
		    			}
	    			}
	    			else 
	    			{
	    				logger.info("Sip Session utility is null, can not handle this event");
	    				CallEventLogger.logEvent(reqId, "Can not handle this event received as the Sip Session utility is not initialized");
	    			}
        		}
        		else logger.info("Will not handle this event");
        			
        	}
        }
        
    }
    
}

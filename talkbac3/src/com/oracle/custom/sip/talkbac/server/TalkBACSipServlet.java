package com.oracle.custom.sip.talkbac.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSessionsUtil;
import javax.servlet.sip.annotation.SipListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.acmepacket.asc.ws.common.CallControlCallResultType;


@SipListener
public class TalkBACSipServlet extends SipServlet implements SipServletListener 
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 6413129334237394838L;
	static private ASCCallControlClient cc;
	static String asc_address;
	static String asc_port;
	static String asc_username;
	static String asc_password;
	static int session_timeout_min;

	static Logger logger = Logger.getLogger(TalkBACSipServlet.class);


	@Resource
	public static SipFactory sipFactory;

	@Resource
	public static SipSessionsUtil util;

	@Override
	public void servletInitialized(SipServletContextEvent event) {
		logger.info("TalkBACSipServlet initialized...............................................BEGIN");
		try 
		{
			CDRLogger.initialize();
			CallEventLogger.initialize();
			readProperties();
			cc = new ASCCallControlClient(asc_address, Integer.parseInt(asc_port));
			cc.authenticate(asc_password, asc_password);
		}
		catch (Exception e) 
		{
			logger.error(e);
			e.printStackTrace();
		}
		logger.info("TalkBACSipServlet initialized...............................................END");
	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		logger.info("Response " + response.getStatus() + " " + response.getReasonPhrase());
	}

	@Override
	protected void doMessage(SipServletRequest request) throws ServletException, IOException {
			logger.info("doMessage..." + request);
			String requestId = "";
			HashMap<String, String> content = null;
			try
			{
				content = parseContent(request.getContent().toString());
				requestId = content.get(TalkbacUtil.REQUEST_ID);
				if(content.get(TalkbacUtil.CALL_CONTROL) == null)
				{
					throw new Exception("Invalid content, there is no call_control element");
				}
			}
			catch(Exception e)
			{
				logger.info("can not parse content");
				logger.error(e);
				e.printStackTrace();
				request.createResponse(500).send();
			}
			/********************/
			switch (content.get(TalkbacUtil.CALL_CONTROL).hashCode()) 
			{
			case 3045982: // call
				//parsing content for properties and sending make call command to asc
				SipApplicationSession appSession = null;
				try
				{
					CallControlCallResultType result = null;
					appSession = sipFactory.createApplicationSessionByKey(requestId);
					CallEventLogger.logEvent(requestId, "Sip application session created");
					appSession.setInvalidateWhenReady(false);
					appSession.setExpires(session_timeout_min);
					appSession.setAttribute(TalkbacUtil.INITIAL_REQUEST, request);
					CallEventLogger.logEvent(requestId, "Received Make Call request with values" + content);
					appSession.setAttribute(TalkbacUtil.IS_ORIGIN_CONNECTING, false);
					appSession.setAttribute(TalkbacUtil.IS_DEST_CONNECTING, false);
					appSession.setAttribute(TalkbacUtil.IS_DEST_CONNECTED, false);
					appSession.setAttribute(TalkbacUtil.IS_ORIGIN_CONNECTED, false);
					appSession.setAttribute(TalkbacUtil.IS_DIST_TERMINATED, false);
					appSession.setAttribute(TalkbacUtil.IS_ORIGIN_TERMINATED, false);
					appSession.setAttribute(TalkbacUtil.IS_TIME_OUT_DISCONNECT_RAISED, false);
					appSession.setAttribute(TalkbacUtil.REQUEST_ID, requestId);
					appSession.setAttribute(TalkbacUtil.ORIGIN_ADDR, content.get(TalkbacUtil.ORIGIN));
					appSession.setAttribute(TalkbacUtil.DEST_ADDR, content.get(TalkbacUtil.DESTINATION));
					appSession.setAttribute(TalkbacUtil.USER_ID, request.getFrom().getURI());
					result = cc.call(requestId, content.get(TalkbacUtil.ORIGIN), content.get(TalkbacUtil.DESTINATION));
					appSession.setAttribute(TalkbacUtil.ASC_SESSION_ID, result.getSessionId());
					appSession.setAttribute(TalkbacUtil.ASC_IN_CALL_LEG_HANDLE, result.getInCallLegHandle());
					appSession.setAttribute(TalkbacUtil.ASC_OUT_CALL_LEG_HANDLE, result.getOutCallLegHandle());
					CallEventLogger.logEvent(requestId, "Sent make call request to ASC successfully, Result[origin call handler:" + result.getInCallLegHandle()
							+ ", destination call handler:" + result.getOutCallLegHandle() + "]");
					request.createResponse(200).send();
				}
				catch(Exception e)
				{
					CallEventLogger.logEvent(requestId, "Failed to hadle make call at Talkbac server application, Reason:" + e.getMessage());
					logger.error(e);
					e.printStackTrace();
					request.createResponse(500).send();
					if(appSession !=null)
					{
						appSession.invalidate();
					}
				}
				break;
			case 530405532: // disconnect
				try
				{
					appSession = sipFactory.createApplicationSessionByKey(requestId);
					if(appSession != null)
					{
						long inCallLegId = ((Long)appSession.getAttribute(TalkbacUtil.ASC_IN_CALL_LEG_HANDLE)).longValue();
						cc.disconnect(inCallLegId);
						request.createResponse(200).send();
					}
					else 
					{
						CallEventLogger.logEvent(requestId, "Received disconnect request but can not find application session");
						request.createResponse(404).send();
					}
				}
				catch(Exception e)
				{
					CallEventLogger.logEvent(requestId, "Failed to hadle disconnect at Talkbac server application, Reason:" + e.getMessage());
					logger.error(e);
					e.printStackTrace();
					request.createResponse(500).send();
				}
				break;
			/*************************************
			case 1280882667: // transfer
				result = cc.transfer(requestId, content.get("endpoint"));
				break;
			case 3208383: // hold
				result = cc.hold(requestId);
				break;
			case -310034372: // retrieve
				result = cc.retrieve(requestId);
				break;
			case 3083120: // dial
				result = cc.dial(requestId, content.get("digits"));
				break;
			case 3363353: // mute
				result = cc.mute(requestId);
				break;
			case -295947265: // un_mute
				result = cc.un_mute(requestId);
				break;
			case -776144932: // redirect
				result = cc.redirect(requestId, content.get("endpoint"));
				break;
			case -1423461112: // accept
				result = cc.accept(requestId, content.get("endpoint"));
				break;
			case -934710369: // reject
				result = cc.reject(requestId, content.get("endpoint"));
				break;
			****************************/
			}
	}
	
	public static void processEvent(String requestId, SipApplicationSession appSession, EventType type, EventCode code, boolean isLast)
	{
		try
		{
			SipServletRequest initialRequest = (SipServletRequest) appSession.getAttribute(TalkbacUtil.INITIAL_REQUEST);
			HashMap<String, String> response_map = new HashMap<String, String>();
			response_map.put(TalkbacUtil.EVENT_TYPE, type.getValue());
			response_map.put(TalkbacUtil.REQUEST_ID, requestId);
			response_map.put(TalkbacUtil.STATUS_CODE, code.getCode());
			response_map.put(TalkbacUtil.REASON, code.getReason()); 
			ObjectMapper objectMapper = new ObjectMapper();
			String response_string = objectMapper.writeValueAsString(response_map);
			javax.servlet.sip.Address fromAdd = sipFactory.createAddress(TalkbacUtil.SERVER_URL);
			SipServletRequest result = sipFactory.createRequest(appSession, "MESSAGE", fromAdd, initialRequest.getFrom());
			result.setContent(response_string, "text/plain");
			result.setRequestURI(initialRequest.getFrom().getURI());
			result.send();
			CallEventLogger.logEvent(requestId, "Sent event to client [event_type:" + type.getValue() +", status_code:" + code.getCode() 
					+ ", reason:" + code.getReason() + "]");
		}
		catch(Exception e)
		{
			e.printStackTrace();
			logger.error(e);
			logger.warn("can not send event to client " + e.getMessage());
			CallEventLogger.logEvent(requestId, "Failed to send event to client [event_type:" + type.getValue() +", status_code:" + code.getCode() 
					+ ", reason:" + code.getReason() + "], error:" + e.getMessage());
		}
		if(isLast) appSession.invalidate();
	}

	private static HashMap<String,String> parseContent(String str)
	{
		HashMap<String, String> content = new HashMap<String, String>();
		str = str.substring(1);
		str = str.substring(0, str.length() -1);
		String[] pairs = str.split(",");
		for (int i=0;i<pairs.length;i++) 
		{
		    String pair = pairs[i];
		    String[] keyValue = pair.split("=");
		    keyValue[0] = keyValue[0].trim();
		    keyValue[1] = keyValue[1].trim();
		    content.put(keyValue[0],keyValue[1]);
		}
		logger.info(content.toString());
		return content;
	}
	
	public static void readProperties() throws Exception
	{
		logger.info("Reading properties from talkbac.xml file");
		DocumentBuilderFactory builderFactory =
		        DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = null;
		try 
		{
		    builder = builderFactory.newDocumentBuilder();
		    Document document = builder.parse(
		            new FileInputStream(TalkbacUtil.CONFIG_FILE));
		    document.getDocumentElement().normalize();
		    NodeList nodes = document.getElementsByTagName("property");
		    for (int temp = 0; temp < nodes.getLength(); temp++) 
		    {
		    	Node nNode = nodes.item(temp);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) 
				{
					Element eElement = (Element) nNode;	
					String name = eElement.getElementsByTagName("name").item(0).getChildNodes().item(0).getNodeValue().trim();
					if(name.equalsIgnoreCase(TalkbacUtil.ASC_ADDR))
					{
						asc_address = eElement.getElementsByTagName("value").item(0).getChildNodes().item(0).getNodeValue().trim();
						logger.info("Read asc address " + asc_address);
					}
					else if(name.equalsIgnoreCase(TalkbacUtil.ASC_PORT))
					{
						asc_port = eElement.getElementsByTagName("value").item(0).getChildNodes().item(0).getNodeValue().trim();
						logger.info("Read asc port " + asc_port);
					}
					if(name.equalsIgnoreCase(TalkbacUtil.ASC_UNAME))
					{
						asc_username = eElement.getElementsByTagName("value").item(0).getChildNodes().item(0).getNodeValue().trim();
						logger.info("Read asc username " + asc_username);
					}
					if(name.equalsIgnoreCase(TalkbacUtil.ASC_PWD))
					{
						asc_password = eElement.getElementsByTagName("value").item(0).getChildNodes().item(0).getNodeValue().trim();
						logger.info("Read asc pwd " + asc_password);
					}
					if(name.equalsIgnoreCase(TalkbacUtil.MAX_SESSION_TIMEOUT))
					{
						session_timeout_min = Integer.valueOf(eElement.getElementsByTagName("value").item(0).getChildNodes().item(0).getNodeValue().trim());
						logger.info("Read max session timeout " + session_timeout_min);
					}
				}
		    }
		}
		catch (Exception e)
		{
			e.printStackTrace();
			logger.error(e);
		    logger.error("Can not read talkbac properties file " + e.getMessage()); 
		}
	}
	
	public static void disconnectOnTimeout(SipApplicationSession appSession)
	{
		//on app session timeout call should be terminated and event should be sent to client
		logger.warn("On app session timeout disconnecting call");
		String reqId = null;
		try
		{
			reqId = (String)appSession.getAttribute(TalkbacUtil.REQUEST_ID);
			CallEventLogger.logEvent(reqId, "disconnecting call due to max call duration timeout");
			long inCallLegId = ((Long)appSession.getAttribute(TalkbacUtil.ASC_IN_CALL_LEG_HANDLE)).longValue();
			cc.disconnect(inCallLegId);
			processEvent(reqId, appSession, EventType.CALL_CONTROL, EventCode.TIME_OUT, false);
		}
		catch(Exception e)
		{
			logger.warn("Can not disconnect call on application session timeout");
			CallEventLogger.logEvent(reqId, "Unable to disconnect call on timeout, error " + e.getMessage() + " , however call session will be cleared on OCCAS in next 60 seconds");
			logger.error(e);
			e.printStackTrace();
		}
		appSession.setAttribute(TalkbacUtil.IS_TIME_OUT_DISCONNECT_RAISED, true);
		//sending event to client		
	}
	
}

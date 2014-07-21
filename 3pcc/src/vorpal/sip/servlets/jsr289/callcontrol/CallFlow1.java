/*
 * http://tools.ietf.org/html/rfc3725
 *
 * 4.1.  Flow I
 *
 *             A              Controller               B
 *             |(1) INVITE no SDP  |                   |
 *             |<------------------|                   |
 *             |(2) 200 offer1     |                   |
 *             |------------------>|                   |
 *             |                   |(3) INVITE offer1  |
 *             |                   |------------------>|
 *             |                   |(4) 200 OK answer1 |
 *             |                   |<------------------|
 *             |                   |(5) ACK            |
 *             |                   |------------------>|
 *             |(6) ACK answer1    |                   |
 *             |<------------------|                   |
 *             |(7) RTP            |                   |
 *             |.......................................|
 *
 */

package vorpal.sip.servlets.jsr289.callcontrol;

import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import weblogic.kernel.KernelLogManager;

public class CallFlow1 extends CallStateHandler {
	static Logger logger;
	{
		logger = Logger.getLogger(CallFlow1.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}
	
	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletRequest originRequest;
	SipServletResponse originResponse;


	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession;
		
		switch (state) {

		case 1:
			this.origin = request.getFrom();
			this.destination = request.getTo();
			this.initiator = request;
			
			
			appSession = request.getApplicationSession();
			
			destinationRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", origin, destination);			
			originRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", destination, origin);

			if(ThirdPartyCallControlServlet.outboundProxy!=null){
				logger.fine("pushing route: "+ThirdPartyCallControlServlet.outboundProxy);	
				destinationRequest.pushRoute(ThirdPartyCallControlServlet.outboundProxy);

				logger.fine("pushing route: "+ThirdPartyCallControlServlet.outboundProxy);
				originRequest.pushRoute(ThirdPartyCallControlServlet.outboundProxy);
			}
			
			originRequest.send();
			
			logger.fine("-SENDING------------------------------------------------------------------------");
			logger.fine(originRequest.toString());
			

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			
			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());
			
			destinationRequest.getSession().setAttribute(ThirdPartyCallControlServlet.DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			originRequest.getSession().setAttribute(ThirdPartyCallControlServlet.ORIGIN_SESSION_ID, originRequest.getSession().getId());
			
			break;
		case 2:
		case 3: // Response from origin
			if (status == 200) {
				destinationRequest.setContent(response.getContent(), response.getContentType());
				destinationRequest.send();
				
				logger.fine("-SENDING------------------------------------------------------------------------");
				logger.fine(originRequest.toString());
				

				state = 4;
				originResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
				
				initiator.createResponse(183).send();
								
			} else {
				if (initiator != null) {
					System.out.println("Sending... "+response.getStatus());
					initiator.createResponse(response.getStatus(), response.getReasonPhrase()).send();
				}
			}
			break;

		case 4:
		case 5:
		case 6: // Response from destination
			SipServletResponse initResponse = initiator.createResponse(response.getStatus(), response.getReasonPhrase());
			
			if (status == 200) {
				SipServletRequest destinationAck = response.createAck();
				destinationAck.send();

				SipServletRequest originAck = originResponse.createAck();
				originAck.setContent(response.getContent(), response.getContentType());
				originAck.send();

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				state = 7;
				initResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			initResponse.send();
			break;
			
		case 7: //ACK from initiator
			request.getSession().removeAttribute(CALL_STATE_HANDLER);
			break;

		}

	}
}

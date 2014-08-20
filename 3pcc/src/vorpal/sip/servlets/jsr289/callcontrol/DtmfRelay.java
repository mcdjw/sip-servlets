package vorpal.sip.servlets.jsr289.callcontrol;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class DtmfRelay extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		SipApplicationSession appSession = null;

		SipServletRequest initiator;

		switch (state) {
		case 1:
			this.initiator = request;

			appSession = request.getApplicationSession();
			String dsi = (String) request.getApplicationSession().getAttribute(DESTINATION_SESSION_ID);
			
			SipSession sipSession = appSession.getSipSession(dsi);

			System.out.println("Getting DESTINATION_SESSION_ID: "+appSession.getId()+", "+sipSession.getId());

			SipServletRequest dtmfRequest = sipSession.createRequest(request.getMethod());
			dtmfRequest.setContent(request.getContent(), request.getContentType());
			
			String event = request.getHeader("Event");
			if(event!=null){
				dtmfRequest.setHeader("Event", event);
				dtmfRequest.setHeader("Events", event);
			}
			
			
			dtmfRequest.send();
//			System.out.println("Sending INFO: "+dtmfRequest.getSession().getId());
			
			

			state = 2;
			dtmfRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;
		case 2:
			SipServletResponse initiatorResponse = this.initiator.createResponse(response.getStatus(), response.getReasonPhrase());
			initiatorResponse.setContent(response.getContent(), response.getContentType());
			initiatorResponse.send();

			response.removeAttribute(CALL_STATE_HANDLER);
			break;
		}

	}

}

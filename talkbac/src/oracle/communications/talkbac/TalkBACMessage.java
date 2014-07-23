package oracle.communications.talkbac;

import java.io.IOException;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

public class TalkBACMessage {
	ObjectNode objectNode;
	SipServletRequest message;

	TalkBACMessage(SipServletMessage ssm, String event) {
		SipApplicationSession appSession = ssm.getApplicationSession();
		SipSession sipSession = ssm.getSession();
		
		ObjectMapper objectMapper = new ObjectMapper();
		objectNode = objectMapper.createObjectNode();
		
		String requestId = (String) sipSession.getAttribute(TalkBACSipServlet.REQUEST_ID);
		if(requestId==null){
			requestId = (String) appSession.getAttribute(TalkBACSipServlet.REQUEST_ID);
		}
		objectNode.put("request_id", requestId);
		objectNode.put("event", event);

		if(ssm instanceof SipServletResponse){
			objectNode.put("status", ((SipServletResponse) ssm).getStatus());
			objectNode.put("reason", ((SipServletResponse) ssm).getReasonPhrase());
		}
		
		
		Address client_address = (Address) appSession.getAttribute(TalkBACSipServlet.CLIENT_ADDRESS);
		Address application_address = (Address) appSession.getAttribute(TalkBACSipServlet.APPLICATION_ADDRESS);
		
		message = TalkBACSipServlet.factory.createRequest(appSession, "MESSAGE", application_address, client_address);
	}

	void setParameter(String name, String value) {
		objectNode.put(name, value);
	}

	String getParameter(String name) {
		return objectNode.getTextValue();
	}

	void send() throws IOException {
		message.setContent(objectNode.toString(), "text/plain");
		message.send();
	}

}

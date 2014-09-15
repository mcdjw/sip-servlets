package oracle.communications.talkbac;

import javax.servlet.sip.Proxy;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class KeepAlive extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		SipApplicationSession appSession = request.getApplicationSession();

		if (request.isInitial()) {

			int expires = request.getExpires();
			if (expires != 0) {
				appSession.setExpires(expires);
			} else {
				// request.getApplicationSession().invalidate();
				CallStateHandler handler = new TerminateCall();
				handler.processEvent(request, response, timer);
			}

			Proxy proxy = request.getProxy();
			proxy.setAddToPath(true);
			proxy.setRecordRoute(true);
			proxy.setRecurse(true);
			proxy.setSupervised(true);
			proxy.proxyTo(request.getRequestURI());

		}

	}

}

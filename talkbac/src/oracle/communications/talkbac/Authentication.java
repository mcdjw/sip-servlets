/*
 * SUCCESSFUL REGISTRATION
 *             WSC                     TALKBAC                 LDAP                 REGISTRAR
 * open socket  |                        |                      |                      |
 *              | REGISTER               |                      |                      |
 *              |----------------------->|                      |                      |
 *              | 401 Unauthorized       |                      |                      |
 *              |<-----------------------|                      |                      |
 *              | REGISTER w/credentials |                      |                      |
 *              |----------------------->|                      |                      |
 *              |                        | search w/result      |                      |
 *              |                        |--------------------->|                      |
 *              |                        | REGISTER             |                      |
 *              |                        |-------------------------------------------->|
 *              |                        | 200 OK               |                      |
 *              |                        |<--------------------------------------------|
 *              | 200 OK                 |                      |                      |
 *              |<-----------------------|                      |                      |
 * open socket  |                        |                      |                      |
 *
 * UNSUCCESSFUL REGISTRATION
 *             WSC                     TALKBAC                 LDAP                 REGISTRAR
 * open socket  |                        |                      |                      |
 *              | REGISTER               |                      |                      |
 *              |----------------------->|                      |                      |
 *              | 401 Unauthorized       |                      |                      |
 *              |<-----------------------|                      |                      |
 *              | REGISTER w/credentials |                      |                      |
 *              |----------------------->|                      |                      |
 *              |                        | search w/o result    |                      |
 *              |                        |--------------------->|                      |
 *              | 403 Forbidden          |                      |                      |
 *              |<-----------------------|                      |                      |
 * close socket |                        |                      |                      |
 *
 */

package oracle.communications.talkbac;

import java.util.ListIterator;
import java.util.logging.Logger;

import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import weblogic.kernel.KernelLogManager;

public class Authentication extends CallStateHandler {
	static Logger logger;
	{
		logger = Logger.getLogger(Authentication.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Override
	public void processEvent(SipApplicationSession appSession,  TalkBACMessageUtility msgUtility,SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		String userId = null;
		String pbx = null;
		boolean proxyOn = true;
		int expires = request.getExpires();

		logger.fine("isInitial: " + request.isInitial());
		// if (request.isInitial()) {

		String auth = request.getHeader("Authorization");
		logger.fine("auth: " + auth);
		if (auth == null) {
			SipServletResponse authResponse = request.createResponse(401);
			authResponse.setHeader("WWW-Authenticate",
					"Digest realm=\"oracle.com\", qop=\"auth\",nonce=\"ea9c8e88df84f1cec4341ae6cbe5a359\",opaque=\"\", stale=FALSE, algorithm=MD5");
			authResponse.send();
			this.printOutboundMessage(authResponse);
		} else {
			logger.fine("disableAuth: " + TalkBACSipServlet.disableAuth);
			if (TalkBACSipServlet.disableAuth == false) {

				// do LDAP lookup.

				String strUserId = "username=\"";
				int begin = auth.indexOf(strUserId, 0);
				begin = begin + strUserId.length();
				int end = auth.indexOf("\"", begin);
				userId = auth.substring(begin, end);

				if (userId.contains("@")) {
					userId = userId.substring(0, userId.indexOf("@"));
				}

				logger.fine("userId: " + userId);

				String strSid = "opaque=\"";
				begin = auth.indexOf(strSid, 0);
				begin = begin + strSid.length();
				end = auth.indexOf("\"", begin);
				String objectSid = auth.substring(begin, end);
				logger.fine("objectSid: " + objectSid);

				DirContext ldapCtx = TalkBACSipServlet.connectLdap();

				NamingEnumeration results = TalkBACSipServlet.ldapSearch(ldapCtx, userId, objectSid);
				if (results.hasMoreElements()) {
					SearchResult sr = (SearchResult) results.nextElement();
					logger.fine("TalkBACSipServlet.ldapLocationParameter: " + TalkBACSipServlet.ldapLocationParameter);
					pbx = (String) sr.getAttributes().get(TalkBACSipServlet.ldapLocationParameter).get();
					logger.fine("Authentication pbx: " + pbx + ", " + appSession.getId().hashCode());
					if (pbx != null) {
						appSession.setAttribute(TalkBACSipServlet.GATEWAY, pbx);
					}
				} else {
					proxyOn = false;
					SipServletResponse authResponse = request.createResponse(403);
					authResponse.send();
					this.printOutboundMessage(authResponse);
				}

				TalkBACSipServlet.disconnectLdap(ldapCtx, results);
			} else {
				// set default proxy value
				pbx = request.getHeader(TalkBACSipServlet.GATEWAY);
				if (pbx != null) {
					appSession.setAttribute(TalkBACSipServlet.GATEWAY, pbx);
				}
			}

			// Create AppSession for registered endpoints

			String endpoint;
			SipApplicationSession endpointAppSession;
			ListIterator<String> itr = request.getHeaders("Endpoint");
			if (expires > 0) {
				while (itr.hasNext()) {
					endpoint = itr.next();
					endpointAppSession = TalkBACSipServlet.util.getApplicationSessionByKey(endpoint, true);
					endpointAppSession.setAttribute(TalkBACSipServlet.CLIENT_ADDRESS, request.getTo());
					endpointAppSession.setAttribute(TalkBACSipServlet.USER, ((SipURI) request.getTo().getURI()).getUser());

					if (pbx != null) {
						endpointAppSession.setAttribute(TalkBACSipServlet.GATEWAY, pbx);
					}

					endpointAppSession.setExpires(request.getExpires());
					endpointAppSession.setInvalidateWhenReady(false);
				}
			} else {
				while (itr.hasNext()) {
					endpoint = itr.next();
					endpointAppSession = TalkBACSipServlet.util.getApplicationSessionByKey(endpoint, false);
					if (endpointAppSession != null) {
						endpointAppSession.removeAttribute(TalkBACSipServlet.CLIENT_ADDRESS);
						endpointAppSession.removeAttribute(TalkBACSipServlet.USER);
						endpointAppSession.removeAttribute(TalkBACSipServlet.GATEWAY);
						endpointAppSession.setInvalidateWhenReady(true);
					}
				}
			}

			logger.fine("proxyOn: " + proxyOn);
			if (proxyOn) {
				
				appSession.setExpires(expires);
				appSession.setAttribute(TalkBACSipServlet.CLIENT_ADDRESS, request.getTo());
				appSession.setAttribute(TalkBACSipServlet.USER, ((SipURI) request.getTo().getURI()).getUser());
				
				if (expires > 0) {
					appSession.setInvalidateWhenReady(false);
				} else {
					appSession.setInvalidateWhenReady(true);
				}

				Proxy proxy = request.getProxy();
				proxy.proxyTo(request.getRequestURI());

			}

		}

	}

}

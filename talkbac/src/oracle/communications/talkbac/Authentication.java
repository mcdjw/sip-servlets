package oracle.communications.talkbac;

import java.util.logging.Logger;

import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchResult;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import weblogic.kernel.KernelLogManager;

public class Authentication extends CallStateHandler {
	static Logger logger;
	{
		logger = Logger.getLogger(Authentication.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {

		if (request.isInitial()) {

			String auth = request.getHeader("Authorization");
			if (auth == null && false == TalkBACSipServlet.disableAuth) {
				SipServletResponse authResponse = request.createResponse(401);
				authResponse
						.setHeader(
								"WWW-Authenticate",
								"Digest realm=\"oracle.com\", qop=\"auth\",nonce=\"ea9c8e88df84f1cec4341ae6cbe5a359\",opaque=\"\", stale=FALSE, algorithm=MD5");
				authResponse.send();
			} else {
				// do LDP lookup.

				// Authorization: SID
				// username="jeff@mcdonald.net",realm="null",cnonce="0f9681da257b88ec7428a8b8b3c18d71",nc=00000001,qop=auth,opaque="1234",uri="sip:192.168.1.202:5060",nonce="null",response="7be61f1b44cd8d81d089d90e0365222e",algorithm=null

				String strUserId = "username=\"";
				int begin = auth.indexOf(strUserId, 0);
				begin = begin + strUserId.length();
				int end = auth.indexOf("\"", begin);
				String userId = auth.substring(begin, end);

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

				NamingEnumeration results = TalkBACSipServlet.ldapSearch(userId, objectSid);
				if (results.hasMoreElements()) {
					SipApplicationSession appSession = request.getApplicationSession();

					SearchResult sr = (SearchResult) results.nextElement();

					logger.fine("TalkBACSipServlet.ldapLocationParameter: "
							+ TalkBACSipServlet.ldapLocationParameter);

					String pbx = (String) sr.getAttributes().get(TalkBACSipServlet.ldapLocationParameter).get();

					logger.fine("Authentication pbx: " + pbx+", "+appSession.getId().hashCode());

					if (pbx != null) {
						appSession.setAttribute("PBX", pbx);
					}

					int expires = request.getExpires();
					appSession.setExpires(expires);
					logger.fine("Expires: "+expires);
					if (expires > 0) {
						appSession.setInvalidateWhenReady(false);
					} else {
						appSession.setInvalidateWhenReady(true);
					}

					Proxy proxy = request.getProxy();
					// proxy.setAddToPath(true);
					// proxy.setRecordRoute(true);
					// proxy.setRecurse(true);
					// proxy.setSupervised(true);
					proxy.proxyTo(request.getRequestURI());

				} else {
					SipServletResponse authResponse = request.createResponse(403);
					authResponse.send();
				}

			}

		}

	}

}

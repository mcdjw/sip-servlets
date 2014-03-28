/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.fsmar;

import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.spi.SipApplicationRouterProvider;

public class AppRouterProvider extends SipApplicationRouterProvider {
	private static final AppRouter appRouter = new AppRouter();

	@Override
	public SipApplicationRouter getSipApplicationRouter() {
		return appRouter;
	}

}

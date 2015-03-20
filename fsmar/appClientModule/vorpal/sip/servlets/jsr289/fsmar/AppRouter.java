/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.fsmar;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.SipApplicationRouterInfo;
import javax.servlet.sip.ar.SipApplicationRoutingDirective;
import javax.servlet.sip.ar.SipApplicationRoutingRegion;
import javax.servlet.sip.ar.SipRouteModifier;
import javax.servlet.sip.ar.SipTargetedRequestInfo;

import weblogic.kernel.KernelLogManager;

import com.bea.wcp.sip.engine.SipServletRequestAdapter;
import com.bea.wcp.sip.engine.server.SipApplicationSessionImpl;

public class AppRouter implements SipApplicationRouter {
	static Logger logger;
	{
		logger = Logger.getLogger(AppRouter.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	HashMap<String, LinkedList<Transition>> transitionSet;
	HashSet<String> deployed = new HashSet<String>();

	@Override
	public void init() {
	}

	@Override
	public void applicationDeployed(List<String> apps) {
		logger.info("Application(s) Deployed:" + Arrays.toString(apps.toArray()));
		deployed.addAll(apps);
	}

	@Override
	public void applicationUndeployed(List<String> apps) {
		logger.info("Application(s) Undeployed:" + Arrays.toString(apps.toArray()));
		deployed.removeAll(apps);
	}

	@Override
	public void destroy() {
	}

	@Override
	public SipApplicationRouterInfo getNextApplication(SipServletRequest request, SipApplicationRoutingRegion region, SipApplicationRoutingDirective directive,
			SipTargetedRequestInfo requestInfo, Serializable state) {

		SipApplicationRouterInfo nextApp = null;
		String previous = "null";

		// For targeted sessions, skip all this nonsense and route to that app
		if (requestInfo != null) {
			return new SipApplicationRouterInfo(requestInfo.getApplicationName(), region, null, null, SipRouteModifier.NO_ROUTE, state);
		}
		
		// Is the request intended for a deployed app?
		if (nextApp == null) {
			Address address = request.getTo();
			if(address.getURI().isSipURI()){
				SipURI sipURI = (SipURI)address.getURI();
				String user = sipURI.getUser();
				if (deployed.contains(user)) {
					logger.fine("Matching deployed app: "+user);
					return new SipApplicationRouterInfo(user, region, null, null, SipRouteModifier.NO_ROUTE, previous);
				}				
			}
		}		
		
		// Did the request originate from an app?
		SipApplicationSessionImpl sasi = ((SipServletRequestAdapter) request).getImpl().getSipApplicationSessionImpl();
		if (sasi != null) {
			previous = sasi.getApplicationName();
		}

		logger.fine("previous: "+previous+", request: "+request.getMethod()+", Transition Set: "+Arrays.toString( transitionSet.keySet().toArray() ) );
		
		
		String ts_key = null;
		String ts_value = null;

		// Find the transition with the previous state
		LinkedList<Transition> transitions = transitionSet.get(previous);
		if (transitions != null) {
			for (Transition t : transitions) {
				if (true == t.condition.matches(request, region, directive, requestInfo)) {
					nextApp = t.action.execute(request, region, directive, requestInfo);
					ts_key = t.key;
					ts_value = t.value;
					break;
				}
			}
		}
		


		if (logger.isLoggable(Level.FINE)) {
			logger.fine("Matching Transition Set: [" + ts_key + ": " + ts_value + "]");
		}

		return nextApp;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void init(Properties props) {

		transitionSet = new HashMap<String, LinkedList<Transition>>();

		for (Entry entry : props.entrySet()) {
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();

			Transition t = new Transition(key, value);

			LinkedList<Transition> list = transitionSet.get(t.previous);
			if (list == null) {
				list = new LinkedList<Transition>();
			}

			list.add(t);
			transitionSet.put(t.previous, list);
		}
	}

}

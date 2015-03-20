/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.fsmar;

import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.ar.SipApplicationRouterInfo;
import javax.servlet.sip.ar.SipApplicationRoutingDirective;
import javax.servlet.sip.ar.SipApplicationRoutingRegion;
import javax.servlet.sip.ar.SipApplicationRoutingRegionType;
import javax.servlet.sip.ar.SipRouteModifier;
import javax.servlet.sip.ar.SipTargetedRequestInfo;

public class Action {

	SipApplicationRoutingRegionType regionType = SipApplicationRoutingRegionType.NEUTRAL;
	String label;
	String subscriber;
	String[] routes = null;
	SipRouteModifier mod = SipRouteModifier.NO_ROUTE;
	String stateInfo;
	String nextApplicationName;
	
	@Override
	public String toString(){
		return "{"+regionType+", "+subscriber+", "+mod+"}";
	}

	public Action(String state, String commands) {
		this.stateInfo = state;
		this.nextApplicationName = state;

		String command, operator;

		StringTokenizer tokenizer = new StringTokenizer(commands, "|");
		while (tokenizer.hasMoreTokens()) {
			command = tokenizer.nextToken();

			if (command.contains("(")) {
				operator = command.substring(0, command.indexOf('('));
			} else {
				operator = command;
			}

			int hashcode = operator.hashCode();
			switch (hashcode) {

			case 0x04A8BA29: // ROUTE
			case 0x550BED3D: // ROUTE_BACK
				mod = SipRouteModifier.valueOf(operator);

				String str_routes = command.substring(command.indexOf('(') + 1, command.indexOf(')'));
				StringTokenizer routeTokenizer = new StringTokenizer(str_routes, "|");
				LinkedList<String> listRoutes = new LinkedList<String>();
				while (tokenizer.hasMoreTokens()) {
					listRoutes.add(routeTokenizer.nextToken());
				}
				this.routes = (String[]) listRoutes.toArray();
				break;

			case 0xE78CA749: // ORIGINATING
			case 0x0DF85ABE: // TERMINATING
				regionType = SipApplicationRoutingRegionType.valueOf(operator);

				label = command.substring(command.indexOf('(') + 1, command.indexOf(')'));
				subscriber = command.substring(command.indexOf('(') + 1, command.indexOf(')'));
				// what does label do?
				break;
			}

		}

	}

	public SipApplicationRouterInfo execute(SipServletRequest request, SipApplicationRoutingRegion region,
			SipApplicationRoutingDirective directive, SipTargetedRequestInfo requestInfo) {

		String subscriberURI = null;
		SipApplicationRoutingRegion routingRegion = new SipApplicationRoutingRegion(label, regionType);

		if (subscriber != null) {
			subscriberURI = request.getHeader(subscriber);
		}

		return new SipApplicationRouterInfo(nextApplicationName, routingRegion, subscriberURI, routes, mod, stateInfo);

	}

}

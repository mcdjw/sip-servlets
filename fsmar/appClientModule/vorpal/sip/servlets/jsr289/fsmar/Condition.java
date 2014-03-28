/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.fsmar;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.ar.SipApplicationRoutingDirective;
import javax.servlet.sip.ar.SipApplicationRoutingRegion;
import javax.servlet.sip.ar.SipApplicationRoutingRegionType;
import javax.servlet.sip.ar.SipTargetedRequestInfo;

public class Condition {
	private SipApplicationRoutingRegionType region = null;
	private SipApplicationRoutingDirective directive = null;
	private String method = null;
	private HashMap<String, Pattern> headers = new HashMap<String, Pattern>();
	
	boolean matches(SipServletRequest request, SipApplicationRoutingRegion region, SipApplicationRoutingDirective directive,
			SipTargetedRequestInfo info) {

		if (this.method != null && request.getMethod().compareTo(this.method) != 0) {
			return false;
		}

		if (this.region != null && (region == null || (this.region != region.getType()))) {
			return false;
		}

		if (this.directive != null && (directive == null || (this.directive != directive))) {
			return false;
		}

		if (headers != null) {
			Set<Entry<String, Pattern>> entrySet = headers.entrySet();
			for (Entry<String, Pattern> entry : entrySet) {
				Pattern pattern = entry.getValue();
				Matcher matcher = pattern.matcher(request.getHeader(entry.getKey()));
				if (false == matcher.matches()) {
					return false;
				}
			}
		}

		return true;
	}

	Condition(String criteria) {

		StringTokenizer tokenizer = new StringTokenizer(criteria, "|");
		while (tokenizer.hasMoreTokens()) {
			String criterion = tokenizer.nextToken();

			switch (criterion.hashCode()) {

			case 0xE78CA749: // ORIGINATING
			case 0x0DF85ABE: // TERMINATING
			case 0x98B9A9A7: // NEUTRAL
				this.region = SipApplicationRoutingRegionType.valueOf(criterion);
				break;

			case 0x0CD71CA7: // CONTINUE
			case 0x00012D80: // NEW
			case 0x6C59DEC2: // REVERSE
				this.directive = SipApplicationRoutingDirective.valueOf(criterion);
				break;

			case 0x81052309: // INVITE
			case 0x0000FC69: // ACK
			case 0xE052127E: // OPTIONS
			case 0x000102CE: // BYE
			case 0x760D227A: // CANCEL
			case 0x05821EA3: // REGISTER
			case 0xC4C7ED2A: // SUBSCRIBE
			case 0x899A8B49: // NOTIFY
			case 0x63B68BE7: // MESSAGE
			case 0x00225CAE: // INFO
			case 0x048D9B27: // PRACK
			case 0x95932CC9: // UPDATE
			case 0x04A3F460: // REFER
			case 0x1CC428EF: // PUBLISH
				this.method = new String(criterion);
				break;

			default: // everything else must be a header in the form of: From(^.*$)
				String header = criterion.substring(0, criterion.indexOf('('));
				String regex = criterion.substring(criterion.indexOf('(') + 1, criterion.indexOf(')'));
				Pattern pattern = Pattern.compile(regex);
				this.headers.put(header, pattern);
			}

		}

	}

}

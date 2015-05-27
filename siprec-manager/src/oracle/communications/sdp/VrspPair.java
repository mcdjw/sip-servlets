package oracle.communications.sdp;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletRequest;

public class VrspPair {
	public String header;
	public String regularExpression;
	public String primary;
	public String secondary;

	public static Vector<VrspPair> dialPlans = new Vector<VrspPair>();

	public VrspPair(String str) {
		List<String> items = Arrays.asList(str.split("\\s*,\\s*"));

		this.header = items.get(0);
		this.regularExpression = items.get(1);
		this.primary = items.get(2);
		this.secondary = items.get(3);

	}

	public static void initialize(SipServletContextEvent event) {
		String name;
		String value;
		Enumeration<String> parameterNames = event.getServletContext().getInitParameterNames();
		while (parameterNames.hasMoreElements()) {
			name = parameterNames.nextElement();
			if (name.contains("dial_plan")) {
				value = event.getServletContext().getInitParameter(name);
				System.out.println("adding dial plans: " + value);
				dialPlans.addElement(new VrspPair(value));
			}
		}
	}

	public static VrspPair findMatchingVrspPair(SipServletRequest request) {
		VrspPair vrspPair = null;
		String header;
		for (VrspPair vp : dialPlans) {

			header = vp.getHeader();

			if (header != null && header.equals(null) == false) {
				if (request.getHeader(header).matches(vp.getRegularExpression())) {
					vrspPair = vp;
					break;
				}
			} else {
				if (request.getRequestURI().toString().matches(vp.getRegularExpression())) {
					vrspPair = vp;
					break;
				}
			}

		}

		return vrspPair;
	}

	public String getHeader() {
		return header;
	}

	public void setHeader(String header) {
		this.header = header;
	}

	public String getRegularExpression() {
		return regularExpression;
	}

	public void setRegularExpression(String regularExpression) {
		this.regularExpression = regularExpression;
	}

	public String getPrimary() {
		return primary;
	}

	public void setPrimary(String primary) {
		this.primary = primary;
	}

	public String getSecondary() {
		return secondary;
	}

	public void setSecondary(String secondary) {
		this.secondary = secondary;
	}

}

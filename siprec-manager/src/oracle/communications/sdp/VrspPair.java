package oracle.communications.sdp;

import java.util.Arrays;
import java.util.List;

public class VrspPair {
	public String dialPlan;
	public String primary;
	public String secondary;

	public VrspPair(String str) {

		List<String> items = Arrays.asList(str.split("\\s*,\\s*"));
		this.dialPlan = items.get(0);
		this.primary = items.get(1);
		this.secondary = items.get(2);

	}

	public String getDialPlan() {
		return dialPlan;
	}

	public void setDialPlan(String dialPlan) {
		this.dialPlan = dialPlan;
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

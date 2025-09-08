package org.metricshub.jawk;

import org.metricshub.jawk.NotImplementedError;
import org.metricshub.jawk.ext.AbstractExtension;
import org.metricshub.jawk.jrt.AssocArray;
import org.metricshub.jawk.jrt.JRT;
import org.metricshub.jawk.jrt.VariableManager;
import org.metricshub.jawk.util.AwkSettings;

public class FallbackTestExtension extends AbstractExtension {

	private static final String MY_EXTENSION_FUNCTION = "myExtensionFunction";
	private int invokeCount;

	@Override
	public void init(VariableManager vm, JRT jrt, AwkSettings settings) {}

	@Override
	public String getExtensionName() {
		return "FallbackTestExtension";
	}

	@Override
	public String[] extensionKeywords() {
		return new String[] { MY_EXTENSION_FUNCTION };
	}

	@Override
	public int[] getAssocArrayParameterPositions(String extensionKeyword, int numArgs) {
		if (MY_EXTENSION_FUNCTION.equals(extensionKeyword)) {
			return new int[] { 1 };
		}
		return new int[] {};
	}

	private Object doMyExtension(Object[] args) {
		StringBuilder result = new StringBuilder();
		int count = ((Long) args[0]).intValue();
		AssocArray array = (AssocArray) args[1];
		for (int i = 0; i < count; i++) {
			for (Object item : array.keySet()) {
				result.append((String) array.get(item));
			}
		}
		return result.toString();
	}

	@Override
	public Object invoke(String keyword, Object[] args) {
		invokeCount++;
		if (MY_EXTENSION_FUNCTION.equals(keyword)) {
			return doMyExtension(args);
		}
		throw new NotImplementedError(keyword + " is not implemented by " + getExtensionName());
	}

	int getInvokeCount() {
		return invokeCount;
	}
}

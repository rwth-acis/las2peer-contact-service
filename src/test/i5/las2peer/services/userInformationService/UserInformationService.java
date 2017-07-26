package i5.las2peer.services.userInformationService;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import i5.las2peer.api.Service;

public class UserInformationService extends Service {

	public boolean setPermissions(Map<String, Boolean> permissions) {
		return true;
	}

	public Map<String, Boolean> getPermissions(String[] fields) {
		return new HashMap<String, Boolean>();
	}

	public boolean set(Map<String, Serializable> values) {
		return true;
	}

	public Map<String, Serializable> get(String agentId, String[] fields) {
		return new HashMap<String, Serializable>();
	}
}

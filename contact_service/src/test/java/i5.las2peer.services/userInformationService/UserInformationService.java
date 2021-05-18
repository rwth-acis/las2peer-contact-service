package i5.las2peer.services.userInformationService;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import i5.las2peer.api.Service;

public class UserInformationService extends Service {
	static String status = "working";

	public Object setPermissions(Map<String, Object> permissions) {
		if((boolean)permissions.get("firstName") ==false) {
			status = "null";
			return null;
		}else if((boolean) permissions.get("lastName") == false) {
			status = "wrong";
			return "";
		}
		return true;
	}

	public Object getPermissions(String[] fields) {
		if(status.equals("null")) {
			status = "working";
			return null;
		}else if(status.equals("wrong")) {
			status = "working";
			return "";
		}
		return new HashMap<String, Boolean>();
	}

	public Object set(Map<String, Serializable> values) {
		if(values.get("firstName").equals("false")) {
			status = "null";
			return null;
		}else if(values.get("lastName").equals("false")) {
			status = "wrong";
			return "";
		}
		return true;
	}

	public Object get(String agentId, String[] fields) {
		if(status.equals("null")) {
			status = "working";
			return null;
		}else if(status.equals("wrong")) {
			status = "working";
			return "";
		}
		return new HashMap<String, Serializable>();
	}
}

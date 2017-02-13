package i5.las2peer.services.userInformationService;

import java.util.HashMap;
import java.util.Map;

import i5.las2peer.api.Service;

public class UserInformationService extends Service{
	
	public void setPermissions(Map<String, Boolean> map){
		
	}
	
	public Map<String,Boolean> getPermissions(){
		return new HashMap<String,Boolean>();
	}
	public void set(Map<String,String> map){
		
	}
	
	public Map<String,String> get(long id,Map<String,String> fields){
		return new HashMap<String,String>();
	}
}

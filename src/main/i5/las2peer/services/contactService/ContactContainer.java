package i5.las2peer.services.contactService;

import java.io.Serializable;
import java.util.HashSet;
import java.util.HashMap;

/**
 * This is an example object used to persist some data (in this case a simple String) to the network storage. It can be
 * replaced with any type of Serializable or even with a plain String object.
 * 
 */
public class ContactContainer implements Serializable {

	private static final long serialVersionUID = 1L;

	private HashSet<Long> userList;
	private HashMap<String, Long> groups;

	public ContactContainer() {
		userList = new HashSet<Long>();
		groups = new HashMap<String, Long>();
	}

	public void addContact(Long id) {
		userList.add(id);
	}

	public void addGroup(String name, Long id) {
		groups.put(name, id);
	}
	
	public HashSet<Long> getUserList(){
		return userList;
	}
	
	public void removeContact(Long id){
		userList.remove(id);
	}
	
	public HashMap<String,Long> getGroups(){
		return groups;
	}
	
	public Long getGroupId(String name){
		return groups.get(name);
	}
	
	public void removeGroup(Long id){
		groups.remove(id);
	}
	

}
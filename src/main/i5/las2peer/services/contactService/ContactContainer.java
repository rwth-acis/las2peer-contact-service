package i5.las2peer.services.contactService;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This is an example object used to persist some data (in this case a simple String) to the network storage. It can be
 * replaced with any type of Serializable or even with a plain String object.
 * 
 */
public class ContactContainer implements Serializable {

	private static final long serialVersionUID = 1L;

	private HashSet<String> userList;
	private HashMap<String, String> groups;

	public ContactContainer() {
		userList = new HashSet<String>();
		groups = new HashMap<String, String>();
	}

	public boolean addContact(String id) {
		return userList.add(id);
	}

	public void addGroup(String name, String id) {
		groups.put(name, id);
	}

	public HashSet<String> getUserList() {
		return userList;
	}

	public boolean removeContact(String id) {
		return userList.remove(id);
	}

	public HashMap<String, String> getGroups() {
		return groups;
	}

	public String getGroupId(String name) {
		return groups.get(name);
	}

	public void removeGroup(String name) {
		groups.remove(name);
	}

}
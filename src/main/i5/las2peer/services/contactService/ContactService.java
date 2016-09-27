package i5.las2peer.services.contactService;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import i5.las2peer.api.exceptions.ArtifactNotFoundException;
import i5.las2peer.api.exceptions.StorageException;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.AgentAlreadyRegisteredException;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.MediaType;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.security.Agent;
import i5.las2peer.security.AgentException;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.security.UserAgent;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.SerializationException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

/**
 * las2peer Contact Service
 * 
 * This service can manage your las2peer contacts and groups It uses the las2peer Web-Connector for RESTful access to
 * it.
 * 
 */

@Path("/contacts")
@Api
@SwaggerDefinition(
		info = @Info(
				title = "laspeer Contact Service",
				version = "1.0",
				description = "A las2peer Contact Service for managing your contacts and groups.",
				termsOfService = "",
				contact = @Contact(
						name = "Alexander Neumann",
						url = "",
						email = "alexander.tobias.neumann@rwth-aachen.de"),
				license = @License(
						name = "",
						url = "")))

public class ContactService extends RESTService {

	// instantiate the logger class
	private final L2pLogger logger = L2pLogger.getInstance(ContactService.class.getName());
	private final String contact_prefix = "contacts_";
	private final String group_prefix = "groups_";
	private final String address_prefix = "addressbook";

	public ContactService() {
		// read and set properties values
		// IF THE SERVICE CLASS NAME IS CHANGED, THE PROPERTIES FILE NAME NEED TO BE CHANGED TOO!
		setFieldValues();
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Adds a contact to your list
	 * 
	 * @param name Login name of the contact you want to add
	 * @return Returns a HttpResponse
	 */
	@GET
	@Path("/contact/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "addContact",
			notes = "Add a contact")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Contact added"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse addContact(@PathParam("value") String name) {
		// Setting owner and identifier for envelope
		Agent owner = getContext().getMainAgent();
		String identifier = contact_prefix + owner.getId();
		Envelope env = null;
		boolean added = false;
		long userID = -1;

		// try to fetch user you want to add
		try {
			userID = getContext().getLocalNode().getAgentIdForLogin(name);
		} catch (AgentNotKnownException ex) {
			return new HttpResponse("Agent does not exist", HttpURLConnection.HTTP_NOT_FOUND);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Unknown Error", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		// try to get envelope
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();

			added = cc.addContact(userID);
			env = getContext().createEnvelope(stored, cc);
		} catch (ArtifactNotFoundException e) {
			ContactContainer cc = new ContactContainer();
			try {
				added = cc.addContact(userID);
				env = getContext().createEnvelope(identifier, cc);
			} catch (IllegalArgumentException | SerializationException | CryptoException e1) {
				logger.log(Level.SEVERE, "Unknown error!", e);
				e1.printStackTrace();
			}
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Unknown Error", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		// try to store envelope
		storeEnvelope(env);

		if (added)
			return new HttpResponse("Contact added", HttpURLConnection.HTTP_OK);
		else
			return new HttpResponse("Contact already in list", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	/**
	 * Removes a contact from your list
	 * 
	 * @param name Login name of the contact you want to delete
	 * @return Returns a HttpResponse
	 */
	@POST
	@Path("/contact/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Contact removed"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	@ApiOperation(
			value = "removeContact",
			notes = "removes a contact")
	public HttpResponse removeContact(@PathParam("value") String name) {
		Agent owner = getContext().getMainAgent();
		String identifier = contact_prefix + owner.getId();
		Envelope env = null;
		boolean deleted = false;

		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			long userID = getContext().getLocalNode().getAgentIdForLogin(name);
			deleted = cc.removeContact(userID);
			env = getContext().createEnvelope(stored, cc);
		} catch (ArtifactNotFoundException e) {
			ContactContainer cc = new ContactContainer();
			try {
				env = getContext().createEnvelope(identifier, cc);
			} catch (IllegalArgumentException | SerializationException | CryptoException e1) {
				logger.log(Level.SEVERE, "Unknown error!", e);
				e1.printStackTrace();
			}
			deleted = true;
		} catch (AgentNotKnownException ex) {
			return new HttpResponse("Agent does not exist", HttpURLConnection.HTTP_BAD_REQUEST);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Could not delete Contact", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		storeEnvelope(env);
		if (deleted)
			return new HttpResponse("Contact removed", HttpURLConnection.HTTP_OK);
		else
			return new HttpResponse("User is not one of your contacts.", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	/**
	 * Removes a contact from your list
	 * 
	 * @return An Arraylist containing all your contacts
	 */
	public ArrayList<String> getContacts() {
		Agent owner = getContext().getMainAgent();
		String identifier = contact_prefix + owner.getId();
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			HashSet<Long> userList = cc.getUserList();
			return getNames(userList);
		} catch (ArtifactNotFoundException e) {
			ContactContainer cc = new ContactContainer();
			HashSet<Long> userList = cc.getUserList();
			return getNames(userList);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	/**
	 * Adds a group
	 * 
	 * @param content Name of your group
	 * @return Returns a HttpResponse
	 * 
	 */
	@POST
	@Path("/group")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	@ApiOperation(
			value = "createGroup",
			notes = "Creates a group")
	public HttpResponse addGroup(@ContentParam String content) {
		// Setting owner group members
		Agent[] members = new Agent[1];
		members[0] = getContext().getMainAgent();
		String identifier = group_prefix + members[0].getId();
		Envelope env = null;
		boolean added = false;
		long id = -1;
		JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
		JSONObject params;
		// try to parse conetent
		try {
			params = (JSONObject) parser.parse(content);
		} catch (ParseException e2) {
			return new HttpResponse("Fehler", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		// group name
		String name = (String) params.get("name");
		GroupAgent groupAgent;
		// try to create group
		try {
			groupAgent = GroupAgent.createGroupAgent(members);
		} catch (L2pSecurityException | CryptoException | SerializationException e2) {
			return new HttpResponse("Fehler", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		id = groupAgent.getId();
		try {
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			getContext().getLocalNode().storeAgent(groupAgent);

			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			cc.addGroup(name, groupAgent.getId());
			added = true;
			env = getContext().createEnvelope(stored, cc);
		} catch (ArtifactNotFoundException e) {
			ContactContainer cc = new ContactContainer();
			cc.addGroup(name, groupAgent.getId());
			added = true;
			try {
				env = getContext().createEnvelope(identifier, cc);
			} catch (IllegalArgumentException | SerializationException | CryptoException e1) {
				logger.log(Level.SEVERE, "Unknown error!", e);
				e1.printStackTrace();
			}
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			e.printStackTrace();
			return new HttpResponse("Fehler", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		storeEnvelope(env, groupAgent);
		if (added)
			return new HttpResponse("" + id, HttpURLConnection.HTTP_OK);
		else
			return new HttpResponse("Fehler", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	/**
	 * Get a group
	 * 
	 * @param name Name of your group
	 * @return Returns a HttpResponse
	 * 
	 */
	@GET
	@Path("/groupID/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "getGroup",
			notes = "Get a group via name")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getGroupByName(@PathParam("value") String name) {
		Agent owner = getContext().getMainAgent();
		String identifier = group_prefix + owner.getId();
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			Long id = cc.getGroupId(name);
			return new HttpResponse("" + id, HttpURLConnection.HTTP_OK);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			e.printStackTrace();
		}
		return new HttpResponse("Error getting groupID", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	/**
	 * Removes a group
	 * 
	 * @param content Name of the group you want to delete
	 * @return Returns a HttpResponse
	 */
	@POST
	@Path("/group/remove")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Group removed"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	@ApiOperation(
			value = "removeGroup",
			notes = "removes a group")
	public HttpResponse removeGroup(@ContentParam String content) {
		Agent owner = getContext().getMainAgent();
		String identifier = group_prefix + owner.getId();
		Envelope env = null;
		try {
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject) parser.parse(content);
			String name = (String) params.get("name");
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			long groupID = cc.getGroups().get(name);
			cc.removeGroup(groupID);
			env = getContext().createEnvelope(stored, cc);
			GroupAgent ga = getContext().requestGroupAgent(groupID);
			ga.removeMember(getContext().getMainAgent());
			getContext().getLocalNode().storeAgent(ga);
			storeEnvelope(env);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Fehler", HttpURLConnection.HTTP_OK);
		}
		storeEnvelope(env);
		return new HttpResponse("Ok", HttpURLConnection.HTTP_OK);
	}

	/**
	 * Adds a member to a group
	 * 
	 * @param content Name of the group and the user
	 * @return Returns a HttpResponse
	 */
	@POST
	@Path("/groupmember")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Update successfull"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	@ApiOperation(
			value = "addGroupMember",
			notes = "Add a member to a group")
	public HttpResponse addGroupMember(@ContentParam String content) {
		Agent owner = getContext().getMainAgent();
		String identifier = group_prefix + owner.getId();
		Envelope env = null;
		long addID = -1;
		GroupAgent groupAgent = null;
		try {
			// Parse data
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject) parser.parse(content);
			String groupName = (String) params.get("groupName");
			String userName = (String) params.get("userName");

			// Get envelope
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			groupAgent = getContext().requestGroupAgent(cc.getGroups().get(groupName));
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			addID = getContext().getLocalNode().getAgentIdForLogin(userName);
			groupAgent.addMember(getContext().getAgent(addID));
			env = getContext().createEnvelope(stored, cc);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't add member!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Fehler", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		storeEnvelope(env, groupAgent);
		try {
			getContext().getLocalNode().storeAgent(groupAgent);
		} catch (AgentAlreadyRegisteredException e) {
			// Agent already registered.
		} catch (L2pSecurityException e) {
			// Security Warning
			e.printStackTrace();
			return new HttpResponse("Something went wrong", HttpURLConnection.HTTP_BAD_REQUEST);
		} catch (AgentException e) {
			// Agent not found?
			e.printStackTrace();
			return new HttpResponse("GroupAgent not found", HttpURLConnection.HTTP_NOT_FOUND);
		}
		return new HttpResponse("Added to group.", HttpURLConnection.HTTP_OK);
	}

	/**
	 * Removes a member of a group
	 * 
	 * @param content Name of the group and the user
	 * @return Returns a HttpResponse
	 */
	@POST
	@Path("/groupmember/remove")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Update successfull"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	@ApiOperation(
			value = "removeGroupMember",
			notes = "Add a member to a group")
	public HttpResponse removeGroupMember(@ContentParam String content) {

		Agent owner = getContext().getMainAgent();
		String identifier = group_prefix + owner.getId();
		Envelope env = null;
		GroupAgent groupAgent = null;
		try {
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject) parser.parse(content);
			String groupName = (String) params.get("groupName");
			String userName = (String) params.get("userName");
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			groupAgent = getContext().requestGroupAgent(cc.getGroups().get(groupName));
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			long addID = getContext().getLocalNode().getAgentIdForLogin(userName);
			groupAgent.removeMember(getContext().getAgent(addID));
			env = getContext().createEnvelope(stored, cc);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't remove member!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Fehler", HttpURLConnection.HTTP_OK);
		}
		storeEnvelope(env, groupAgent);
		try {
			getContext().getLocalNode().storeAgent(groupAgent);
		} catch (AgentAlreadyRegisteredException e) {
			// Agent already registered.
		} catch (L2pSecurityException e) {
			// Security Warning
			e.printStackTrace();
			return new HttpResponse("Something went wrong", HttpURLConnection.HTTP_BAD_REQUEST);
		} catch (AgentException e) {
			// Agent not found?
			e.printStackTrace();
			return new HttpResponse("GroupAgent not found", HttpURLConnection.HTTP_NOT_FOUND);
		}
		return new HttpResponse("Removed from group.", HttpURLConnection.HTTP_OK);
	}

	public ArrayList<String> getGroupMembers(String name) {
		Agent owner = getContext().getMainAgent();
		String identifier = group_prefix + owner.getId();
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			GroupAgent groupAgent = (GroupAgent) getContext().getLocalNode().getAgent(cc.getGroups().get(name));
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			Long[] membersIds = groupAgent.getMemberList();
			return getNames(membersIds);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't get member names!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	public Set<String> getGroupNames() {
		Agent owner = getContext().getMainAgent();
		String identifier = group_prefix + owner.getId();
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			return cc.getGroups().keySet();
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	public ArrayList<String> getNames(Long[] list) {
		try {
			ArrayList<String> names = new ArrayList<String>();
			for (int i = 0; i < list.length; i++) {
				UserAgent user = (UserAgent) getContext().getAgent(list[i]);
				names.add(user.getLoginName());
			}
			return names;
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't get names!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	public ArrayList<String> getNames(HashSet<Long> list) {
		try {
			ArrayList<String> names = new ArrayList<String>();
			for (Long l : list) {
				UserAgent user = (UserAgent) getContext().getAgent(l);
				names.add(user.getLoginName());
			}
			return names;
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't get names!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	@GET
	@Path("/contacts")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "getContacts",
			notes = "Get all contacts")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getContactsREST() {
		ArrayList<String> userList = getContacts();
		JSONObject result = new JSONObject();
		result.put("users", userList);
		String returnString = "" + result;// +getContext().getMainAgent().getId();
		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}

	@GET
	@Path("/group/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "getGroup",
			notes = "Get a group via id")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getGroupREST(@PathParam("value") String groupID) {
		long id = Long.parseLong(groupID);
		String returnString = "GroupAgent";
		try {
			GroupAgent a = getContext().requestGroupAgent(id);
			returnString = a.getId() + "";
		} catch (Exception e) {
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			e.printStackTrace();
		}

		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}

	@GET
	@Path("/group")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "getGroup",
			notes = "get all your groups")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getGroupsREST() {
		String returnString = "Error";
		try {
			returnString = getGroupNames() + "";
		} catch (Exception e) {
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			e.printStackTrace();
		}

		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}

	@GET
	@Path("/groupmember/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "getGroupMember",
			notes = "get all members of your group")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getGroupMemberREST(@PathParam("value") String name) {
		String rs = "Error";
		try {
			rs = getGroupMembers(name).toString();
		} catch (Exception e) {
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse(rs, HttpURLConnection.HTTP_OK);
	}

	@POST
	@Path("/addressbook/add")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "addToAddressBook",
			notes = "Add yourself to the address book")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Added"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse addToAddressBook() {
		Agent owner = getContext().getMainAgent();
		String identifier = address_prefix;
		Envelope env = null;
		boolean added = false;
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			added = cc.addContact(owner.getId());
			env = getContext().createUnencryptedEnvelope(stored, cc);
		} catch (ArtifactNotFoundException ex) {
			ContactContainer cc = new ContactContainer();
			added = cc.addContact(owner.getId());
			try {
				env = getContext().createUnencryptedEnvelope(identifier, cc);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SerializationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CryptoException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Unknown Error", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		try {
			getContext().storeEnvelope(env);
		} catch (StorageException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (added)
			return new HttpResponse("Added to addressbook", HttpURLConnection.HTTP_OK);
		else
			return new HttpResponse("Already in list", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	@POST
	@Path("/addressbook/remove")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "removeFromAddressBook",
			notes = "Add yourself to the address book")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Added"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse removeFromAddressBook() {
		String identifier = address_prefix;
		Envelope env = null;
		boolean deleted = false;
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			long userID = getContext().getMainAgent().getId();
			deleted = cc.removeContact(userID);
			if (deleted)
				env = getContext().createUnencryptedEnvelope(stored, cc);
		} catch (ArtifactNotFoundException ex) {
			ContactContainer cc = new ContactContainer();
			try {
				env = getContext().createUnencryptedEnvelope(identifier, cc);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SerializationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CryptoException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Could not be removed from the list.", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		if (deleted) {
			try {
				getContext().storeEnvelope(env);
			} catch (StorageException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return new HttpResponse("Removed from list.", HttpURLConnection.HTTP_OK);
		} else {
			return new HttpResponse("You were not in the list.", HttpURLConnection.HTTP_BAD_REQUEST);
		}
	}

	@GET
	@Path("/addressbook")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "getAddressBook",
			notes = "get all contacts from the addressbook")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Contacts received"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getAddressBook() {
		String identifier = address_prefix;
		try {
			Envelope stored = getContext().fetchEnvelope(identifier);
			ContactContainer cc = (ContactContainer) stored.getContent();
			HashSet<Long> list = cc.getUserList();
			ArrayList<String> userList = getNames(list);
			JSONObject result = new JSONObject();
			result.put("users", userList);
			String returnString = "" + result;// +getContext().getMainAgent().getId();
			return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
		} catch (ArtifactNotFoundException ex) {
			Envelope env = null;
			ContactContainer cc = new ContactContainer();
			try {
				env = getContext().createUnencryptedEnvelope(identifier, cc);
				storeEnvelope(env);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SerializationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CryptoException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			return new HttpResponse("Could not get any contacts", HttpURLConnection.HTTP_BAD_REQUEST);
		}
		return new HttpResponse("Could not get any contacts", HttpURLConnection.HTTP_BAD_REQUEST);
	}
	// //////////////////////////////////////////////////////////////////////////////////////
	// RMI Calls
	// //////////////////////////////////////////////////////////////////////////////////////

	@POST
	@Path("/user")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Update successfull"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	@ApiOperation(
			value = "updateUserInformation",
			notes = "Updates the name and the userimage")
	public HttpResponse updateUserInformationREST(@ContentParam String content) {
		try {
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject) parser.parse(content);
			HashMap<String, Serializable> m = new HashMap<String, Serializable>();
			m.put("firstName", (String) params.get("firstName"));
			m.put("lastName", (String) params.get("lastName"));
			m.put("userImage", (String) params.get("userImage"));
			// RMI call without parameters
			Object result = this.invokeServiceMethod(
					"i5.las2peer.services.userInformationService.UserInformationService@0.1", "set",
					new Serializable[] { m });
			if (result != null) {

			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse("", HttpURLConnection.HTTP_OK);
	}

	@GET
	@Path("/user")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "getUserInformation",
			notes = "Returns the name and the userimage")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getUserInformation() {
		String returnString = "";
		try {
			// RMI call without parameters
			String[] fields = { "firstName", "lastName", "userImage" };
			Object result = this.invokeServiceMethod(
					"i5.las2peer.services.userInformationService.UserInformationService@0.1", "get",
					new Serializable[] { getContext().getMainAgent().getId(), fields });
			if (result != null) {
				@SuppressWarnings({ "unchecked" })
				HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
				returnString = hashMap.toString();
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}

	@GET
	@Path("/user/{name}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "getUserInformation",
			notes = "Returns the name and the userimage")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getUserInformation(@PathParam("name") String name) {
		String returnString = "";
		try {
			// RMI call without parameters
			String[] fields = { "firstName", "lastName", "userImage" };
			Object result = this.invokeServiceMethod(
					"i5.las2peer.services.userInformationService.UserInformationService@0.1", "get",
					new Serializable[] { getContext().getLocalNode().getAgentIdForLogin(name), fields });
			if (result != null) {
				@SuppressWarnings({ "unchecked" })
				HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
				returnString = hashMap.toString();
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}

	@GET
	@Path("/permission")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "getUserPermission",
			notes = "Returns a field of permissions")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	public HttpResponse getUserPermissions() {
		String returnString = "No Response";
		try {
			// RMI call
			String[] fields = { "firstName", "lastName", "userImage" };
			Object result = this.invokeServiceMethod(
					"i5.las2peer.services.userInformationService.UserInformationService@0.1", "getPermissions",
					new Serializable[] { fields });
			if (result != null) {
				@SuppressWarnings({ "unchecked" })
				HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
				System.out.println(hashMap.toString());
				returnString = hashMap.toString();
			} else {
				return new HttpResponse("Setting permission failed.", HttpURLConnection.HTTP_BAD_REQUEST);
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			e.printStackTrace();
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}

	@POST
	@Path("/permission")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Update successfull"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_UNAUTHORIZED,
							message = "Unauthorized") })
	@ApiOperation(
			value = "updateUserPermission",
			notes = "Updates the name and the userimage")
	public HttpResponse updateUserPermissionREST(@ContentParam String content) {
		try {
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject) parser.parse(content);
			HashMap<String, Serializable> m = new HashMap<String, Serializable>();
			m.put("firstName", (Boolean) params.get("firstName"));
			m.put("lastName", (Boolean) params.get("lastName"));
			m.put("userImage", (Boolean) params.get("userImage"));
			// RMI call without parameters
			Object result = this.invokeServiceMethod(
					"i5.las2peer.services.userInformationService.UserInformationService@0.1", "setPermissions",
					new Serializable[] { m });
			if (result != null) {
				System.out.println("setting permission: " + ((Boolean) result));
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse("", HttpURLConnection.HTTP_OK);
	}

	/**
	 * Function to get the login name of an agent
	 * 
	 * @param id The id of the agent
	 * @return The login name
	 */
	@GET
	@Path("/name/{id}")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Name"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_NOT_FOUND,
							message = "Not Found"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_INTERNAL_ERROR,
							message = "Internal error") })
	@ApiOperation(
			value = "name",
			notes = "get the name of an agent")
	public HttpResponse getName(@PathParam("id") String id) {

		long agentid = Long.parseLong(id);
		try {
			UserAgent user = (UserAgent) getContext().getAgent(agentid);
			String name = user.getLoginName();
			return new HttpResponse(name, HttpURLConnection.HTTP_OK);
		} catch (AgentNotKnownException e) {
			String error = "Agent not found";
			return new HttpResponse(error, HttpURLConnection.HTTP_NOT_FOUND);
		} catch (Exception e) {
			String error = "Internal error";
			return new HttpResponse(error, HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
	}

	// envelope methods
	private void storeEnvelope(Envelope env) {
		try {
			getContext().storeEnvelope(env);
		} catch (StorageException e) {

			e.printStackTrace();
		}
	}

	// envelope methods
	private void storeEnvelope(Envelope env, Agent owner) {
		try {
			getContext().storeEnvelope(env, owner);
		} catch (StorageException e) {

			e.printStackTrace();
		}
	}
}

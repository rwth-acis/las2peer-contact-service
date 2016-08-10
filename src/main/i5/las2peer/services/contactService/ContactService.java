package i5.las2peer.services.contactService;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
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

import i5.las2peer.api.Service;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.p2p.ArtifactNotFoundException;
import i5.las2peer.p2p.StorageException;
import i5.las2peer.persistency.DecodingFailedException;
import i5.las2peer.persistency.EncodingFailedException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.persistency.EnvelopeException;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.MediaType;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.restMapper.tools.ValidationResult;
import i5.las2peer.restMapper.tools.XMLCheck;
import i5.las2peer.security.Agent;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.security.UserAgent;
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


/**
 * las2peer Contact Service
 * 
 * This service can manage your las2peer contacts and groups
 * It uses the las2peer Web-Connector for RESTful access to it.
 * 
 */

@Path("/contacts")
@Version("1.0") // this annotation is used by the XML mapper
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
						email = "alexander.tobias.neumann@rwth-aachen.de"
						),
				license = @License(
						name = "",
						url = ""
						)
				))

public class ContactService extends Service {

	// instantiate the logger class
	private final L2pLogger logger = L2pLogger.getInstance(ContactService.class.getName());
	private final String contact_prefix = "contacts_";
	private final String group_prefix = "groups_";

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
	 * @param name
	 * 			Login name of the contact you want to add
	 * @return 
	 * 			Returns a HttpResponse 
	 */
	@GET
	@Path("/contact/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(value = "addContact",
	notes = "Add a contact")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Contact added"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse addContact(@PathParam("value") String name) {
		try{
			Envelope env = load(contact_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			long userID = getContext().getLocalNode().getAgentIdForLogin(name);
			boolean added = cc.addContact(userID);
			env.updateContent(cc);
			store(env);
			if(added)
				return new HttpResponse("Contact added", HttpURLConnection.HTTP_OK);
			else
				return new HttpResponse("Contact already in list", HttpURLConnection.HTTP_BAD_REQUEST);
		} catch(AgentNotKnownException ex){
			return new HttpResponse("Agent does not exist", HttpURLConnection.HTTP_BAD_REQUEST);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse("Unknown Error", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	/**
	 * Removes a contact from your list
	 * 
	 * @param name
	 * 			Login name of the contact you want to delete
	 * @return 
	 * 			Returns a HttpResponse 
	 */
	@POST
	@Path("/contact/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Contact removed"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	@ApiOperation(value = "removeContact",
	notes = "removes a contact")
	public HttpResponse removeContact(@PathParam("value") String name) {
		try{
			Envelope env = load(contact_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			long userID = getContext().getLocalNode().getAgentIdForLogin(name);
			boolean deleted = cc.removeContact(userID);
			env.updateContent(cc);
			store(env);
			if(deleted)
				return new HttpResponse("Contact removed", HttpURLConnection.HTTP_OK);
			else
				return new HttpResponse("User is not one of your contacts.", HttpURLConnection.HTTP_BAD_REQUEST);
		} catch(AgentNotKnownException ex){
			return new HttpResponse("Agent does not exist", HttpURLConnection.HTTP_BAD_REQUEST);
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse("Could not delete Contact", HttpURLConnection.HTTP_BAD_REQUEST);
	}
	
	/**
	 * Removes a contact from your list
	 * 
	 * @return An Arraylist containing all your contacts
	 */
	public ArrayList<String> getContacts() {
		try{
			Envelope env = load(contact_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			env.updateContent(cc);
			store(env);
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
	 * @param content
	 * 			Name of your group
	 * @return 
	 * 			Returns a HttpResponse 
	 * 
	 */
	@POST
	@Path("/group")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	@ApiOperation(value = "createGroup",
	notes = "Creates a group")
	public HttpResponse addGroup(@ContentParam String content){
		Agent[] members = new Agent[1];
		members[0]= getContext().getMainAgent();
		try{
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject)parser.parse(content);
			String name = (String) params.get("name");
			GroupAgent groupAgent = GroupAgent.createGroupAgent(members);
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			getContext().getLocalNode().storeAgent(groupAgent);
			
			Envelope env = load(group_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			cc.addGroup(name, groupAgent.getId());
			env.updateContent(cc);
			store(env);
			return new HttpResponse(""+groupAgent.getId(), HttpURLConnection.HTTP_OK);
		}catch(Exception e){
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			e.printStackTrace();
		}
		return new HttpResponse("Fehler", HttpURLConnection.HTTP_OK);
	}
	
	/**
	 * Get a group
	 * 
	 * @param name
	 * 			Name of your group
	 * @return 
	 * 			Returns a HttpResponse 
	 * 
	 */
	@GET
	@Path("/groupID/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(value = "getGroup",
	notes = "Get a group via name")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getGroupByName(@PathParam("value") String name){
		try{
			Envelope env = load(group_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			Long id = cc.getGroupId(name);
			env.updateContent(cc);
			store(env);
			return new HttpResponse(""+id, HttpURLConnection.HTTP_OK);
		}catch(Exception e){
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
	 * @param content
	 * 			Name of the group you want to delete
	 * @return 
	 * 			Returns a HttpResponse 
	 */
	@POST
	@Path("/group/remove")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Group removed"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	@ApiOperation(value = "removeGroup",
	notes = "removes a group")
	public HttpResponse removeGroup(@ContentParam String content){
		try{
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject)parser.parse(content);
			String name = (String) params.get("name");
			Envelope env = load(group_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			cc.removeGroup(cc.getGroups().get(name));
			env.updateContent(cc);
			store(env);
			return new HttpResponse("Ok", HttpURLConnection.HTTP_OK);
		}catch(Exception e){
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse("Fehler", HttpURLConnection.HTTP_OK);
	}
	
	/**
	 * Adds a member to a group
	 * 
	 * @param content
	 * 			Name of the group and the user
	 * @return 
	 * 			Returns a HttpResponse 
	 */
	@POST
	@Path("/groupmember")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Update successfull"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	@ApiOperation(value = "addGroupMember",
	notes = "Add a member to a group")
	public HttpResponse addGroupMember(@ContentParam String content){
		try{
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject)parser.parse(content);
			String groupName = (String) params.get("groupName");
			String userName = (String) params.get("userName");
			Envelope env = load(group_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			GroupAgent groupAgent = (GroupAgent) getContext().getLocalNode().getAgent(cc.getGroups().get(groupName));
			env.updateContent(cc);
			store(env);
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			long addID = getContext().getLocalNode().getAgentIdForLogin(userName);
			groupAgent.addMember(getContext().getAgent(addID));

		}catch(Exception e){
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't add member!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse("Fehler", HttpURLConnection.HTTP_OK);
	}
	
	/**
	 * Removes a member of a group
	 * 
	 * @param content
	 * 			Name of the group and the user
	 * @return 
	 * 			Returns a HttpResponse 
	 */
	@POST
	@Path("/groupmember/remove")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Update successfull"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	@ApiOperation(value = "removeGroupMember",
	notes = "Add a member to a group")
	public HttpResponse removeGroupMember(@ContentParam String content){
		try{
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject)parser.parse(content);
			String groupName = (String) params.get("groupName");
			String userName = (String) params.get("userName");
			Envelope env = load(group_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			GroupAgent groupAgent = (GroupAgent) getContext().getLocalNode().getAgent(cc.getGroups().get(groupName));
			env.updateContent(cc);
			store(env);
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			long addID = getContext().getLocalNode().getAgentIdForLogin(userName);
			groupAgent.removeMember(getContext().getAgent(addID));
		}catch(Exception e){
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't remove member!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse("Fehler", HttpURLConnection.HTTP_OK);
	}

	
	
	public ArrayList<String> getGroupMembers(String name){
		try{
			Envelope env = load(group_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			GroupAgent groupAgent = (GroupAgent) getContext().getLocalNode().getAgent(cc.getGroups().get(name));
			env.updateContent(cc);
			store(env);
			groupAgent.unlockPrivateKey(getContext().getMainAgent());
			Long[] membersIds = groupAgent.getMemberList();
			return getNames(membersIds);
		}catch(Exception e){
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't get member names!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	public Set<String> getGroupNames() {
		try{
			Envelope env = load(group_prefix);
			ContactContainer cc = env.getContent(ContactContainer.class);
			env.updateContent(cc);
			store(env);
			return cc.getGroups().keySet();
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	public ArrayList<String> getNames(Long[] list){
		try{
			ArrayList<String> names = new ArrayList<String>();
			for(int i=0;i<list.length;i++){
				UserAgent user = (UserAgent) getContext().getAgent(list[i]);
				names.add(user.getLoginName());
			}
			return names;
		}catch(Exception e){
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't get names!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	public ArrayList<String> getNames(HashSet<Long> list){
		try{
			ArrayList<String> names = new ArrayList<String>();
			for(Long l : list){
				UserAgent user = (UserAgent) getContext().getAgent(l);
				names.add(user.getLoginName());
			}
			return names;
		}catch(Exception e){
			// write error to logfile and console
			logger.log(Level.SEVERE, "Can't get names!", e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return null;
	}

	//asdf

	@GET
	@Path("/contacts")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "getContacts",
	notes = "Get all contacts")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getContactsREST() {
		ArrayList<String> userList= getContacts();
		JSONObject result = new JSONObject();
		result.put("users", userList);
		String returnString = ""+result;//+getContext().getMainAgent().getId();
		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}
	
	@GET
	@Path("/group/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(value = "getGroup",
	notes = "Get a group via id")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getGroupREST(@PathParam("value") String groupID) {
		long id = Long.parseLong(groupID);
		String returnString = "GroupAgent";
		try{
			GroupAgent a = getContext().requestGroupAgent(id);
			returnString = a.getId()+"";
		}catch(Exception e){
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			e.printStackTrace();
		}
		
		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}
	
	@GET
	@Path("/group")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(value = "getGroup",
	notes = "get all your groups")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getGroupsREST() {
		String returnString = "Error";
		try{
			returnString = getGroupNames()+"";
		}catch(Exception e){
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			e.printStackTrace();
		}
		
		return new HttpResponse(returnString, HttpURLConnection.HTTP_OK);
	}
	
	@GET
	@Path("/groupmember/{value}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(value = "getGroupMember",
	notes = "get all member of your group")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getGroupMemberREST(@PathParam("value") String name) {
		String rs = "Error";
		try {
			rs = getGroupMembers(name).toString();
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new HttpResponse(rs, HttpURLConnection.HTTP_OK);
	}
	
	// //////////////////////////////////////////////////////////////////////////////////////
	// RMI Calls 
	// //////////////////////////////////////////////////////////////////////////////////////
	
	@POST
	@Path("/user")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Update successfull"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	@ApiOperation(value = "updateUserInformation",
	notes = "Updates the name and the userimage")
	public HttpResponse updateUserInformationREST(@ContentParam String content) {
		try {
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject)parser.parse(content);
			HashMap<String, Serializable> m = new HashMap<String, Serializable>();
			m.put("firstName", (String) params.get("firstName"));
			m.put("lastName", (String) params.get("lastName"));
			m.put("userImage", (String) params.get("userImage"));
			// RMI call without parameters
			Object result = this.invokeServiceMethod("i5.las2peer.services.userInformationService.UserInformationService@0.1", "set",
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
	@ApiOperation(value = "getUserInformation",
	notes = "Returns the name and the userimage")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getUserInformation() {
		String returnString = "";
		try {
			// RMI call without parameters
			String[] fields = {"firstName","lastName","userImage"};
			Object result = this.invokeServiceMethod("i5.las2peer.services.userInformationService.UserInformationService@0.1", "get",
					new Serializable[] { getContext().getMainAgent().getId(), fields });
			if (result != null) {
				@SuppressWarnings({"unchecked"})
				HashMap<String, Serializable> hashMap = (HashMap<String,Serializable>)result;
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
	@ApiOperation(value = "getUserInformation",
	notes = "Returns the name and the userimage")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getUserInformation(@PathParam("name") String name) {
		String returnString = "";
		try {
			// RMI call without parameters
			String[] fields = {"firstName","lastName","userImage"};
			Object result = this.invokeServiceMethod("i5.las2peer.services.userInformationService.UserInformationService@0.1", "get",
					new Serializable[] { getContext().getLocalNode().getAgentIdForLogin(name), fields });
			if (result != null) {
				@SuppressWarnings({"unchecked"})
				HashMap<String, Serializable> hashMap = (HashMap<String,Serializable>)result;
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
	@ApiOperation(value = "getUserPermission",
	notes = "Returns a field of permissions")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "REPLACE THIS WITH YOUR OK MESSAGE"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	public HttpResponse getUserPermissions() {
		String returnString = "No Response";
		try {
			// RMI call
			String[] fields = {"firstName","lastName","userImage"};
			Object result = this.invokeServiceMethod("i5.las2peer.services.userInformationService.UserInformationService@0.1", "getPermissions",
					new Serializable[] { fields });
			if (result != null) {
				@SuppressWarnings({"unchecked"})
				HashMap<String, Serializable> hashMap = (HashMap<String,Serializable>)result;
				System.out.println(hashMap.toString());
				returnString = hashMap.toString();
			}else{
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
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Update successfull"),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized")
	})
	@ApiOperation(value = "updateUserPermission",
	notes = "Updates the name and the userimage")
	public HttpResponse updateUserPermissionREST(@ContentParam String content) {
		try {
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params = (JSONObject)parser.parse(content);
			HashMap<String, Serializable> m = new HashMap<String, Serializable>();
			m.put("firstName", (Boolean) params.get("firstName"));
			m.put("lastName", (Boolean) params.get("lastName"));
			m.put("userImage", (Boolean) params.get("userImage"));
			// RMI call without parameters
			Object result = this.invokeServiceMethod("i5.las2peer.services.userInformationService.UserInformationService@0.1", "setPermissions",
					new Serializable[] { m });
			if (result != null) {
				System.out.println("setting permission: "+((Boolean) result));
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
	 * @param id
	 * 		The id of the agent 
	 * @return
	 *		The login name
	 */
	@GET
	@Path("/name/{id}")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Name"),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Not Found"),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal error")
	})
	@ApiOperation(value = "name",
	notes = "get the name of an agent")
	public HttpResponse getName( @PathParam("id") String id){

		long agentid = Long.parseLong(id);
		try{
			UserAgent user = (UserAgent) getContext().getAgent(agentid);
			String name = user.getLoginName();
			return new HttpResponse(name, HttpURLConnection.HTTP_OK);
		}
		catch(AgentNotKnownException e){
			String error = "Agent not found";
			return new HttpResponse(error, HttpURLConnection.HTTP_NOT_FOUND);
		}
		catch(Exception e){
			String error = "Internal error";
			return new HttpResponse(error, HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
	}


	private Envelope load(String prefix) throws L2pSecurityException, StorageException, EnvelopeException,
	UnsupportedEncodingException, SerializationException {
		String identifier = prefix+getContext().getMainAgent().getId();
		try {
			Envelope env = getContext().getStoredObject(ContactContainer.class, identifier);
			try {
				env.open(getContext().getMainAgent());
			} catch (L2pSecurityException e) {
				env.open(getContext().getLocalNode().getAnonymous());
			}
			return env;

		} catch (ArtifactNotFoundException e) {
			Envelope env = Envelope.createClassIdEnvelope(new ContactContainer(), identifier, getContext().getMainAgent());
			env.open(getContext().getMainAgent());
			return env;
		}
	}

	private void store(Envelope env) throws L2pSecurityException, UnsupportedEncodingException,
	EncodingFailedException, SerializationException, StorageException, DecodingFailedException {
		if (getContext().getMainAgent().equals(getContext().getLocalNode().getAnonymous()))
			throw new L2pSecurityException("Data cannot be stored for anonymous!");
		env.addSignature(getContext().getMainAgent());
		env.store();
		env.close();
	}


	// //////////////////////////////////////////////////////////////////////////////////////
	// Methods required by the LAS2peer framework.
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Method for debugging purposes.
	 * Here the concept of restMapping validation is shown.
	 * It is important to check, if all annotations are correct and consistent.
	 * Otherwise the service will not be accessible by the WebConnector.
	 * Best to do it in the unit tests.
	 * To avoid being overlooked/ignored the method is implemented here and not in the test section.
	 * @return true, if mapping correct
	 */
	public boolean debugMapping() {
		String XML_LOCATION = "./restMapping.xml";
		String xml = getRESTMapping();

		try {
			RESTMapper.writeFile(XML_LOCATION, xml);
		} catch (IOException e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, e.toString(), e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}

		XMLCheck validator = new XMLCheck();
		ValidationResult result = validator.validate(xml);

		if (result.isValid()) {
			return true;
		}
		return false;
	}

	/**
	 * This method is needed for every RESTful application in LAS2peer. There is no need to change!
	 * 
	 * @return the mapping
	 */
	public String getRESTMapping() {
		String result = "";
		try {
			result = RESTMapper.getMethodsAsXML(this.getClass());
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, e.toString(), e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return result;
	}

}

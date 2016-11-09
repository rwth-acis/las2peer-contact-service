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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import i5.las2peer.api.Context;
import i5.las2peer.api.exceptions.ArtifactNotFoundException;
import i5.las2peer.api.exceptions.StorageException;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.AgentAlreadyRegisteredException;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
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
@ServicePath("contacts")
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
	private final static L2pLogger logger = L2pLogger.getInstance(ContactService.class.getName());
	private final static String contact_prefix = "contacts_";
	private final static String group_prefix = "groups_";
	private final static String address_prefix = "addressbook";

	public ContactService() {
		// read and set properties values
		// IF THE SERVICE CLASS NAME IS CHANGED, THE PROPERTIES FILE NAME NEED TO BE CHANGED TOO!
		setFieldValues();
	}

	@Override
	protected void initResources() {
		getResourceConfig().register(Resource.class);
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////
	@Path("/") // this is the root resource
	public static class Resource {
		// put here all your service methods

		/**
		 * Adds a contact to your list
		 * 
		 * @param name Login name of the contact you want to add
		 * @return Returns a Response
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
		public Response addContact(@PathParam("value") String name) {
			// Setting owner and identifier for envelope
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = contact_prefix + owner.getId();
			Envelope env = null;
			boolean added = false;
			long userID = -1;

			// try to fetch user you want to add
			try {
				userID = Context.getCurrent().getLocalNode().getAgentIdForLogin(name);
			} catch (AgentNotKnownException ex) {
				return Response.status(Status.NOT_FOUND).entity("Agent does not exist.").build();
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).build();
			}

			// try to get envelope
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();

				added = cc.addContact(userID);
				env = Context.getCurrent().createEnvelope(stored, cc);
			} catch (ArtifactNotFoundException e) {
				ContactContainer cc = new ContactContainer();
				try {
					added = cc.addContact(userID);
					env = Context.getCurrent().createEnvelope(identifier, cc);
				} catch (IllegalArgumentException | SerializationException | CryptoException e1) {
					logger.log(Level.SEVERE, "Unknown error!", e);
					e1.printStackTrace();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).build();
			}
			// try to store envelope
			storeEnvelope(env);

			if (added)
				return Response.status(Status.OK).entity("Contact added.").build();
			else
				return Response.status(Status.BAD_REQUEST).entity("Contact already in list.").build();
		}

		/**
		 * Removes a contact from your list
		 * 
		 * @param name Login name of the contact you want to delete
		 * @return Returns a Response
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
		public Response removeContact(@PathParam("value") String name) {
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = contact_prefix + owner.getId();
			Envelope env = null;
			boolean deleted = false;

			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				long userID = Context.getCurrent().getLocalNode().getAgentIdForLogin(name);
				deleted = cc.removeContact(userID);
				env = Context.getCurrent().createEnvelope(stored, cc);
			} catch (ArtifactNotFoundException e) {
				ContactContainer cc = new ContactContainer();
				try {
					env = Context.getCurrent().createEnvelope(identifier, cc);
				} catch (IllegalArgumentException | SerializationException | CryptoException e1) {
					logger.log(Level.SEVERE, "Unknown error!", e);
					e1.printStackTrace();
				}
				deleted = true;
			} catch (AgentNotKnownException ex) {
				return Response.status(Status.NOT_FOUND).entity("Agent does not exist").build();
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Could not delete Contact").build();
			}
			storeEnvelope(env);
			if (deleted)
				return Response.status(Status.OK).entity("Contact removed.").build();
			else
				return Response.status(Status.NOT_FOUND).entity("User is not one of your contacts.").build();
		}

		/**
		 * Removes a contact from your list
		 * 
		 * @return An Arraylist containing all your contacts
		 */
		public ArrayList<String> getContacts() {
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = contact_prefix + owner.getId();
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
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
		 * @return Returns a Response
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
		public Response addGroup(String content) {
			// Setting owner group members
			Agent[] members = new Agent[1];
			members[0] = Context.getCurrent().getMainAgent();
			Envelope env = null;
			Envelope env2 = null;
			boolean added = false;
			long id = -1;
			JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
			JSONObject params;
			// try to parse conetent
			try {
				params = (JSONObject) parser.parse(content);
			} catch (ParseException e2) {
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			// group name
			String name = (String) params.get("name");
			String identifier = group_prefix + name;
			String identifier2 = group_prefix;
			GroupAgent groupAgent;
			// try to create group
			try {
				groupAgent = GroupAgent.createGroupAgent(members);
			} catch (L2pSecurityException | CryptoException | SerializationException e2) {
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			id = groupAgent.getId();
			try {
				groupAgent.unlockPrivateKey(Context.getCurrent().getMainAgent());
				Context.getCurrent().getLocalNode().storeAgent(groupAgent);

				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				cc.addGroup(name, id);
				env = Context.getCurrent().createEnvelope(stored, cc);
			} catch (ArtifactNotFoundException e) {
				ContactContainer cc = new ContactContainer();
				cc.addGroup(name, id);
				try {
					env = Context.getCurrent().createEnvelope(identifier, cc, groupAgent);
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
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			// writing to user
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier2);
				ContactContainer cc = (ContactContainer) stored.getContent();
				cc.addGroup(name, id);
				added = true;
				env2 = Context.getCurrent().createUnencryptedEnvelope(stored, cc);
			} catch (ArtifactNotFoundException e) {
				ContactContainer cc = new ContactContainer();
				cc.addGroup(name, id);
				added = true;
				try {
					env2 = Context.getCurrent().createUnencryptedEnvelope(identifier2, cc);
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
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			storeEnvelope(env, groupAgent);
			storeEnvelope(env2, Context.getCurrent().getServiceAgent());
			if (added)
				return Response.status(Status.OK).entity("" + id).build();
			else
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
		}

		/**
		 * Get a group
		 * 
		 * @param name Name of your group
		 * @return Returns a Response
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
		public Response getGroupByName(@PathParam("value") String name) {
			String identifier = group_prefix + name;
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				Long id = cc.getGroupId(name);
				return Response.status(Status.OK).entity("" + id).build();
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				e.printStackTrace();
			}
			return Response.status(Status.BAD_REQUEST).entity("Error while getting group id.").build();
		}

		/**
		 * Removes a group
		 * 
		 * @param content Name of the group you want to delete
		 * @return Returns a Response
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
		public Response removeGroup(String content) {
			Envelope env = null;
			try {
				JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
				JSONObject params = (JSONObject) parser.parse(content);
				String name = (String) params.get("name");
				String identifier = group_prefix + name;
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				long groupID = cc.getGroups().get(name);
				cc.removeGroup(groupID);
				env = Context.getCurrent().createEnvelope(stored, cc);
				GroupAgent ga = Context.getCurrent().requestGroupAgent(groupID);
				ga.removeMember(Context.getCurrent().getMainAgent());
				Context.getCurrent().getLocalNode().storeAgent(ga);
				storeEnvelope(env);
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			storeEnvelope(env);
			return Response.status(Status.OK).build();
		}

		/**
		 * Adds a member to a group
		 * 
		 * @param content Name of the group and the user
		 * @return Returns a Response
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
		public Response addGroupMember(String content) {
			Envelope env = null;
			long addID = -1;
			GroupAgent groupAgent = null;
			try {
				// Parse data
				JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
				JSONObject params = (JSONObject) parser.parse(content);
				String groupName = (String) params.get("groupName");
				String userName = (String) params.get("userName");

				String identifier = group_prefix + groupName;
				// Get envelope
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				groupAgent = Context.getCurrent().requestGroupAgent(cc.getGroups().get(groupName));
				groupAgent.unlockPrivateKey(Context.getCurrent().getMainAgent());
				addID = Context.getCurrent().getLocalNode().getAgentIdForLogin(userName);
				groupAgent.addMember(Context.getCurrent().getAgent(addID));
				env = Context.getCurrent().createEnvelope(stored, cc);
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't add member!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			storeEnvelope(env, groupAgent);
			try {
				Context.getCurrent().getLocalNode().updateAgent(groupAgent);
			} catch (AgentAlreadyRegisteredException e) {
				// Agent already registered.
			} catch (L2pSecurityException | StorageException e) {
				// Security Warning
				e.printStackTrace();
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			} catch (AgentException e) {
				// Agent not found?
				e.printStackTrace();
				return Response.status(Status.NOT_FOUND).entity("GroupAgent not found.").build();
			}
			return Response.status(Status.OK).entity("Added to gorup.").build();
		}

		/**
		 * Removes a member of a group
		 * 
		 * @param content Name of the group and the user
		 * @return Returns a Response
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
		public Response removeGroupMember(String content) {
			Envelope env = null;
			GroupAgent groupAgent = null;
			try {
				JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
				JSONObject params = (JSONObject) parser.parse(content);
				String groupName = (String) params.get("groupName");
				String userName = (String) params.get("userName");
				String identifier = group_prefix + groupName;
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				groupAgent = Context.getCurrent().requestGroupAgent(cc.getGroups().get(groupName));
				groupAgent.unlockPrivateKey(Context.getCurrent().getMainAgent());
				long addID = Context.getCurrent().getLocalNode().getAgentIdForLogin(userName);
				groupAgent.removeMember(Context.getCurrent().getAgent(addID));
				env = Context.getCurrent().createEnvelope(stored, cc);
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't remove member!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			storeEnvelope(env, groupAgent);
			try {
				Context.getCurrent().getLocalNode().storeAgent(groupAgent);
			} catch (AgentAlreadyRegisteredException e) {
				// Agent already registered.
			} catch (L2pSecurityException e) {
				// Security Warning
				e.printStackTrace();
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			} catch (AgentException e) {
				// Agent not found?
				e.printStackTrace();
				return Response.status(Status.NOT_FOUND).entity("GroupAgent not found.").build();
			}
			return Response.status(Status.OK).entity("Removed from group.").build();
		}

		public ArrayList<String> getGroupMembers(String name) {
			String identifier = group_prefix + name;
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				GroupAgent groupAgent = (GroupAgent) Context.getCurrent().getLocalNode()
						.getAgent(cc.getGroups().get(name));
				groupAgent.unlockPrivateKey(Context.getCurrent().getMainAgent());
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
			Agent member = Context.getCurrent().getMainAgent();
			String identifier = group_prefix;
			Set<String> groupsOfUser = new HashSet<String>();
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				Set<String> groupNames = cc.getGroups().keySet();
				for (String s : groupNames) {
					try {
						GroupAgent g = Context.getCurrent().requestGroupAgent(cc.getGroupId(s));
						if (g.isMember(member))
							groupsOfUser.add(s);
					} catch (Exception e) {

					}
				}
				return groupsOfUser;
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return new HashSet<String>();
		}

		public ArrayList<String> getNames(Long[] list) {
			try {
				ArrayList<String> names = new ArrayList<String>();
				for (int i = 0; i < list.length; i++) {
					UserAgent user = (UserAgent) Context.getCurrent().getAgent(list[i]);
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
					UserAgent user = (UserAgent) Context.getCurrent().getAgent(l);
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
		public Response getContactsREST() {
			ArrayList<String> userList = getContacts();
			JSONObject result = new JSONObject();
			result.put("users", userList);
			String returnString = "" + result;// +Context.getCurrent().getMainAgent().getId();
			return Response.status(Status.OK).entity(returnString).build();
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
		public Response getGroupREST(@PathParam("value") String groupID) {
			long id = Long.parseLong(groupID);
			String returnString = "GroupAgent";
			try {
				GroupAgent a = Context.getCurrent().requestGroupAgent(id);
				returnString = a.getId() + "";
			} catch (Exception e) {
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				e.printStackTrace();
			}
			return Response.status(Status.OK).entity(returnString).build();
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
		public Response getGroupsREST() {
			String returnString = "Error";
			try {
				returnString = getGroupNames() + "";
			} catch (Exception e) {
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				e.printStackTrace();
			}
			return Response.status(Status.OK).entity(returnString).build();
		}

		@GET
		@Path("/groupWithId")
		@Produces(MediaType.APPLICATION_JSON)
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
		public Response getGroupsWithIdREST() {
			String identifier = group_prefix;
			try {
				Agent member = Context.getCurrent().getMainAgent();
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				HashMap<String, Long> allGroups = cc.getGroups();
				HashMap<String, String> returnGroups = new HashMap<String, String>();
				Set<String> groupNames = allGroups.keySet();
				for (String s : groupNames) {
					try {
						GroupAgent g = Context.getCurrent().requestGroupAgent(cc.getGroupId(s));
						if (g.isMember(member))
							returnGroups.put(s, cc.getGroupId(s).toString());
					} catch (Exception e) {

					}
				}
				return Response.status(Status.OK).entity(returnGroups).build();
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.OK).entity(new HashMap<String, String>()).build();
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
		public Response getGroupMemberREST(@PathParam("value") String name) {
			String rs = "Error";
			try {
				rs = getGroupMembers(name).toString();
			} catch (Exception e) {
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.OK).entity(rs).build();
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
		public Response addToAddressBook() {
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = address_prefix;
			Envelope env = null;
			boolean added = false;
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				added = cc.addContact(owner.getId());
				env = Context.getCurrent().createUnencryptedEnvelope(stored, cc);
			} catch (ArtifactNotFoundException ex) {
				ContactContainer cc = new ContactContainer();
				added = cc.addContact(owner.getId());
				try {
					env = Context.getCurrent().createUnencryptedEnvelope(identifier, cc);
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
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			try {
				Context.getCurrent().storeEnvelope(env, Context.getCurrent().getLocalNode().getAnonymous());
			} catch (StorageException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (added)
				return Response.status(Status.OK).entity("Added to addressbook.").build();
			else
				return Response.status(Status.BAD_REQUEST).entity("Already in list.").build();
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
		public Response removeFromAddressBook() {
			String identifier = address_prefix;
			Envelope env = null;
			boolean deleted = false;
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				long userID = Context.getCurrent().getMainAgent().getId();
				deleted = cc.removeContact(userID);
				if (deleted)
					env = Context.getCurrent().createUnencryptedEnvelope(stored, cc);
			} catch (ArtifactNotFoundException ex) {
				ContactContainer cc = new ContactContainer();
				try {
					env = Context.getCurrent().createUnencryptedEnvelope(identifier, cc);
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
				return Response.status(Status.OK).entity("Could not be removed from list.").build();
			}
			if (deleted) {
				try {
					Context.getCurrent().storeEnvelope(env, Context.getCurrent().getLocalNode().getAnonymous());
				} catch (StorageException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return Response.status(Status.OK).entity("Removed from list.").build();
			} else {
				return Response.status(Status.OK).entity("You were not in the list.").build();
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
		public Response getAddressBook() {
			String identifier = address_prefix;
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				HashSet<Long> list = cc.getUserList();
				ArrayList<String> userList = getNames(list);
				JSONObject result = new JSONObject();
				result.put("users", userList);
				String returnString = "" + result;// +Context.getCurrent().getMainAgent().getId();
				return Response.status(Status.OK).entity(returnString).build();
			} catch (ArtifactNotFoundException ex) {
				Envelope env = null;
				ContactContainer cc = new ContactContainer();
				try {
					env = Context.getCurrent().createUnencryptedEnvelope(identifier, cc);
					storeEnvelope(env, Context.getCurrent().getLocalNode().getAnonymous());
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
			}
			return Response.status(Status.BAD_REQUEST).entity("Could not get any contacts.").build();
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
		public Response updateUserInformationREST(String content) {
			try {
				JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
				JSONObject params = (JSONObject) parser.parse(content);
				HashMap<String, Serializable> m = new HashMap<String, Serializable>();
				m.put("firstName", (String) params.get("firstName"));
				m.put("lastName", (String) params.get("lastName"));
				m.put("userImage", (String) params.get("userImage"));
				// RMI call without parameters
				Object result = Context.getCurrent().invoke(
						"i5.las2peer.services.userInformationService.UserInformationService@0.1", "set",
						new Serializable[] { m });
				if (result != null) {

				}
			} catch (Exception e) {
				// one may want to handle some exceptions differently
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.OK).build();
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
		public Response getUserInformation() {
			String returnString = "";
			try {
				// RMI call without parameters
				String[] fields = { "firstName", "lastName", "userImage" };
				Object result = Context.getCurrent().invoke(
						"i5.las2peer.services.userInformationService.UserInformationService@0.1", "get",
						new Serializable[] { Context.getCurrent().getMainAgent().getId(), fields });
				if (result != null) {
					@SuppressWarnings({ "unchecked" })
					HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
					returnString = hashMap.toString();
				}
			} catch (Exception e) {
				// one may want to handle some exceptions differently
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.OK).entity(returnString).build();
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
		public Response getUserInformation(@PathParam("name") String name) {
			String returnString = "";
			try {
				// RMI call without parameters
				String[] fields = { "firstName", "lastName", "userImage" };
				Object result = Context.getCurrent().invoke(
						"i5.las2peer.services.userInformationService.UserInformationService@0.1", "get",
						new Serializable[] { Context.getCurrent().getLocalNode().getAgentIdForLogin(name), fields });
				if (result != null) {
					@SuppressWarnings({ "unchecked" })
					HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
					returnString = hashMap.toString();
				}
			} catch (Exception e) {
				// one may want to handle some exceptions differently
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.OK).entity(returnString).build();
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
		public Response getUserPermissions() {
			String returnString = "No Response";
			try {
				// RMI call
				String[] fields = { "firstName", "lastName", "userImage" };
				Object result = Context.getCurrent().invoke(
						"i5.las2peer.services.userInformationService.UserInformationService@0.1", "getPermissions",
						new Serializable[] { fields });
				if (result != null) {
					@SuppressWarnings({ "unchecked" })
					HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
					System.out.println(hashMap.toString());
					returnString = hashMap.toString();
				} else {
					return Response.status(Status.BAD_REQUEST).entity("Setting permissions failed").build();
				}
			} catch (Exception e) {
				// one may want to handle some exceptions differently
				e.printStackTrace();
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.OK).entity(returnString).build();
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
		public Response updateUserPermissionREST(String content) {
			try {
				JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
				JSONObject params = (JSONObject) parser.parse(content);
				HashMap<String, Boolean> m = new HashMap<String, Boolean>();
				m.put("firstName", (Boolean) params.get("firstName"));
				m.put("lastName", (Boolean) params.get("lastName"));
				m.put("userImage", (Boolean) params.get("userImage"));
				// RMI call without parameters
				Object result = Context.getCurrent().invoke(
						"i5.las2peer.services.userInformationService.UserInformationService@0.1", "setPermissions", m);
				if (result != null) {
					System.out.println("setting permission: " + ((Boolean) result));
				}
			} catch (Exception e) {
				// one may want to handle some exceptions differently
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.OK).entity("").build();
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
		public Response getName(@PathParam("id") String id) {

			long agentid = Long.parseLong(id);
			try {
				UserAgent user = (UserAgent) Context.getCurrent().getAgent(agentid);
				String name = user.getLoginName();
				return Response.status(Status.OK).entity(name).build();
			} catch (AgentNotKnownException e) {
				String error = "Agent not found";
				return Response.status(Status.NOT_FOUND).entity(error).build();
			} catch (Exception e) {
				String error = "Internal error";
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build();
			}
		}

		// envelope methods
		private void storeEnvelope(Envelope env) {
			try {
				Context.getCurrent().storeEnvelope(env);
			} catch (StorageException e) {

				e.printStackTrace();
			}
		}

		// envelope methods
		private void storeEnvelope(Envelope env, Agent owner) {
			try {
				Context.getCurrent().storeEnvelope(env, owner);
			} catch (StorageException e) {

				e.printStackTrace();
			}
		}
	}
}

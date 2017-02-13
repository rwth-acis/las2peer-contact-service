package i5.las2peer.services.contactService;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import javax.ws.rs.DELETE;
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

/**
 * las2peer Contact Service
 * 
 * This service can manage your las2peer contacts and groups It uses the las2peer Web-Connector for RESTful access to
 * it.
 * 
 */
@ServicePath("contactservice")
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
		getResourceConfig().register(ContactResource.class);
		getResourceConfig().register(GroupResource.class);
		getResourceConfig().register(AddressBookResource.class);
		getResourceConfig().register(UserResource.class);
		getResourceConfig().register(PermissionResource.class);
		getResourceConfig().register(NameResource.class);
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////
	@Path("/") // this is the root resource
	@Api(
			value = "Contact Resource")
	@SwaggerDefinition(
			info = @Info(
					title = "laspeer Contact Service",
					version = "0.1",
					description = "A las2peer Contact Service for managing your contacts and groups.",
					termsOfService = "",
					contact = @Contact(
							name = "Alexander Neumann",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service",
							email = "neumann@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class ContactResource {
		ContactService service = (ContactService) Context.getCurrent().getService();
		
		// put here all your service methods
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(
				value = "Get Contacts",
				notes = "Get all your contacts.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Got a list of your contacts."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems.") })
		public Response getContacts() {
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = contact_prefix + owner.getId();
			JSONObject result = new JSONObject();
			try {
				try {
					Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
					ContactContainer cc = (ContactContainer) stored.getContent();
					HashSet<Long> userList = cc.getUserList();
					UserAgent user;
					for (Long l : userList) {
						try {
							user = (UserAgent) Context.getCurrent().getAgent(l);
							result.put("" + user.getId(), user.getLoginName());
						} catch (AgentNotKnownException e1) {
							// Skip unknown agents.
						}
					}
				} catch (ArtifactNotFoundException e) {
					ContactContainer cc = new ContactContainer();
					Envelope env = Context.getCurrent().createEnvelope(identifier, cc);
					service.storeEnvelope(env);
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Unknown Error occured!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
			}
			return Response.status(Status.OK).entity(result).build();
		}

		/**
		 * Adds a contact to your list. Information is stored in an envelope which holds all your contacts.
		 * 
		 * @param name Login name of the contact you want to add
		 * @return Returns a Response
		 */
		@POST
		@Path("{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(
				value = "Add Contact",
				notes = "Add a contact to your contact list.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Contact added."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Contact already in list or storage problems."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_NOT_FOUND,
								message = "Agent does not exist.") })
		public Response addContact(@PathParam("name") String name) {
			// Setting owner and identifier for envelope
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = contact_prefix + owner.getId();
			Envelope env = null;
			boolean added = false;
			long userID = -1;

			// try to fetch user you want to add
			try {
				userID = Context.getCurrent().getLocalNode().getAgentIdForLogin(name);
			} catch (L2pSecurityException | AgentNotKnownException ex) {
				return Response.status(Status.NOT_FOUND).entity("Agent does not exist.").build();
			}

			// try to get envelope
			try {
				try {
					Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
					ContactContainer cc = (ContactContainer) stored.getContent();

					added = cc.addContact(userID);
					env = Context.getCurrent().createEnvelope(stored, cc);
				} catch (ArtifactNotFoundException e) {
					ContactContainer cc = new ContactContainer();
					added = cc.addContact(userID);
					env = Context.getCurrent().createEnvelope(identifier, cc);
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Unknown error occured!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).build();
			}
			// try to store envelope
			service.storeEnvelope(env);

			if (added)
				return Response.status(Status.OK).entity("Contact added.").build();
			else
				return Response.status(Status.BAD_REQUEST).entity("Contact already in list.").build();
		}

		/**
		 * Removes a contact from your list.
		 * 
		 * @param name Login name of the contact you want to delete
		 * @return Returns a Response whether the contact was deleted or not.
		 */
		@DELETE
		@Path("{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Contact removed."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Contact not in list or storage problems."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_NOT_FOUND,
								message = "Agent does not exist.") })
		@ApiOperation(
				value = "Remove Contact",
				notes = "Removes a contact from your contact list.")
		public Response removeContact(@PathParam("name") String name) {
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = contact_prefix + owner.getId();
			Envelope env = null;
			boolean deleted = false;
			try {
				try {
					Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
					ContactContainer cc = (ContactContainer) stored.getContent();
					long userID = Context.getCurrent().getLocalNode().getAgentIdForLogin(name);
					deleted = cc.removeContact(userID);
					env = Context.getCurrent().createEnvelope(stored, cc);
				} catch (ArtifactNotFoundException e) {
					ContactContainer cc = new ContactContainer();
					env = Context.getCurrent().createEnvelope(identifier, cc);
				} catch (AgentNotKnownException ex) {
					return Response.status(Status.NOT_FOUND).entity("Agent does not exist").build();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Could not delete Contact").build();
			}
			service.storeEnvelope(env);
			if (deleted)
				return Response.status(Status.OK).entity("Contact removed.").build();
			else
				return Response.status(Status.NOT_FOUND).entity("User is not one of your contacts.").build();
		}
	}
	
	@Path("/groups") // this is the root resource
	@Api(
			value = "Group Resource")
	@SwaggerDefinition(
			info = @Info(
					title = "laspeer Contact Service",
					version = "0.1",
					description = "A las2peer Contact Service for managing your contacts and groups.",
					termsOfService = "",
					contact = @Contact(
							name = "Alexander Neumann",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service",
							email = "neumann@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class GroupResource {
		ContactService service = (ContactService) Context.getCurrent().getService();

		/**
		 * Retrieve a list of all your groups.
		 * 
		 * @return Returns a Response containing a list of your groups
		 */
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(
				value = "Get Groups",
				notes = "Get all your Groups.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Got a list of your groups."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems.") })
		public Response getGroups() {
			Agent member = Context.getCurrent().getMainAgent();
			String identifier = group_prefix;
			JSONObject result = new JSONObject();
			try {
				try {
					Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
					ContactContainer cc = (ContactContainer) stored.getContent();
					Set<String> groupNames = cc.getGroups().keySet();
					GroupAgent group = null;
					long groupId = -1;
					for (String s : groupNames) {
						try {
							groupId = cc.getGroupId(s);
							group = Context.getCurrent().requestGroupAgent(groupId);
							if (group.isMember(member)) {
								result.put("" + groupId, s);
							}
						} catch (Exception e) {
							// Skip agents who are not known or groups wihtout access.
						}
					}
					return Response.status(Status.OK).entity(result).build();
				} catch (ArtifactNotFoundException e) {
					ContactContainer cc = new ContactContainer();
					Envelope env = null;
					env = Context.getCurrent().createUnencryptedEnvelope(identifier, cc);
					service.storeEnvelope(env, Context.getCurrent().getServiceAgent());
					return Response.status(Status.OK).entity(result).build();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.BAD_REQUEST).entity("Unknown error occured.").build();
		}

		/**
		 * Get group information via name.
		 * 
		 * @param name Name of your group
		 * @return Returns a Response
		 * 
		 */
		@GET
		@Path("/{name}")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(
				value = "Get Group from Name",
				notes = "Get a group via name.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Group found."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Group not found or storage problems.") })
		public Response getGroup(@PathParam("name") String name) {
			String identifier = group_prefix + name;
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				Long id = cc.getGroupId(name);

				JSONObject result = new JSONObject();
				result.put("" + id, name);
				return Response.status(Status.OK).entity(result).build();
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
		 * Adds a group. Creates a group agent and stores the name in an enevelope. The evnelope makes it possible to
		 * request the Agent again and an extra envelope encrypted with the service accessible for all users.
		 * 
		 * @param name Name of your group
		 * @return Returns a Response whether the group could be added or not.
		 * 
		 */
		@POST
		@Path("/{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Group created."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems or group already exist.") })
		@ApiOperation(
				value = "Create Group",
				notes = "Creates a group")
		public Response addGroup(@PathParam("name") String name) {
			// Setting owner group members
			Agent[] members = new Agent[1];
			members[0] = Context.getCurrent().getMainAgent();
			Envelope env = null;
			Envelope env2 = null;
			long id = -1;
			String identifier = group_prefix + name;
			String identifier2 = group_prefix;
			GroupAgent groupAgent;

			try {
				Context.getCurrent().fetchEnvelope(identifier);
				return Response.status(Status.BAD_REQUEST).entity("Group already exist").build();
			} catch (ArtifactNotFoundException e) {
				ContactContainer cc = new ContactContainer();
				// try to create group
				try {
					groupAgent = GroupAgent.createGroupAgent(members);
					id = groupAgent.getId();
					groupAgent.unlockPrivateKey(Context.getCurrent().getMainAgent());
					Context.getCurrent().getLocalNode().storeAgent(groupAgent);
				} catch (L2pSecurityException | CryptoException | SerializationException | AgentException e2) {
					return Response.status(Status.BAD_REQUEST).entity("Error").build();
				}
				cc.addGroup(name, id);
				try {
					env = Context.getCurrent().createEnvelope(identifier, cc, groupAgent);
					service.storeEnvelope(env, groupAgent);
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
				// try to add group to group list
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier2);
				ContactContainer cc = (ContactContainer) stored.getContent();
				cc.addGroup(name, id);
				env2 = Context.getCurrent().createUnencryptedEnvelope(stored, cc);
			} catch (ArtifactNotFoundException e) {
				// create new group list
				ContactContainer cc = new ContactContainer();
				cc.addGroup(name, id);
				try {
					env2 = Context.getCurrent().createUnencryptedEnvelope(identifier2, cc);
				} catch (IllegalArgumentException | SerializationException | CryptoException e1) {
					logger.log(Level.SEVERE, "Unknown error!", e);
					e1.printStackTrace();
					return Response.status(Status.BAD_REQUEST).entity("Error").build();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				e.printStackTrace();
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			service.storeEnvelope(env2, Context.getCurrent().getServiceAgent());
			return Response.status(Status.OK).entity("" + id).build();
		}

		/**
		 * Removes a group.
		 * 
		 * @param name Name of the group you want to delete.
		 * @return Returns a Response whether the group could be deleted or not.
		 */
		@DELETE
		@Path("/{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Group removed."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Group does not exist or storage problems.") })
		@ApiOperation(
				value = "Remove Group",
				notes = "Removes a group.")
		public Response removeGroup(@PathParam("name") String name) {
			Envelope env = null;
			try {
				String identifier = group_prefix + name;
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				long groupID = cc.getGroups().get(name);
				cc.removeGroup(groupID);
				env = Context.getCurrent().createEnvelope(stored, cc);
				GroupAgent ga = Context.getCurrent().requestGroupAgent(groupID);
				ga.removeMember(Context.getCurrent().getMainAgent());
				Context.getCurrent().getLocalNode().storeAgent(ga);
				service.storeEnvelope(env);
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			service.storeEnvelope(env);
			return Response.status(Status.OK).build();
		}

		/**
		 * Retrieve all members of a group.
		 * 
		 * @param name Name of the group.
		 * @return Returns a Response with the list of all members.
		 */
		@GET
		@Path("/{name}/member")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(
				value = "Get Group Member",
				notes = "Get all members of your group.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Got all members of a group"),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems.") })
		public Response getGroupMember(@PathParam("name") String name) {
			JSONObject result = new JSONObject();
			String identifier = group_prefix + name;
			try {
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				GroupAgent groupAgent = (GroupAgent) Context.getCurrent().getLocalNode()
						.getAgent(cc.getGroups().get(name));
				groupAgent.unlockPrivateKey(Context.getCurrent().getMainAgent());
				Long[] memberIds = groupAgent.getMemberList();
				for (int i = 0; i < memberIds.length; i++) {
					UserAgent user = (UserAgent) Context.getCurrent().getAgent(memberIds[i]);
					result.put("" + memberIds[i], user.getLoginName());
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't get member names!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
			}
			return Response.status(Status.OK).entity(result).build();
		}

		/**
		 * Adds a member to a group.
		 * 
		 * @param groupName Name of the group.
		 * @param userName Name of the user.
		 * @return Returns a Response
		 */
		@POST
		@Path("/{name}/member/{user}")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Groupmember added."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_NOT_FOUND,
								message = "Agent does not exist.") })
		@ApiOperation(
				value = "Add Group Member",
				notes = "Add a member to a group.")
		public Response addGroupMember(@PathParam("name") String groupName, @PathParam("user") String userName) {
			Envelope env = null;
			long addID = -1;
			GroupAgent groupAgent = null;
			try {
				String identifier = group_prefix + groupName;
				// Get envelope
				Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				groupAgent = Context.getCurrent().requestGroupAgent(cc.getGroups().get(groupName));
				groupAgent.unlockPrivateKey(Context.getCurrent().getMainAgent());
				addID = Context.getCurrent().getLocalNode().getAgentIdForLogin(userName);
				groupAgent.addMember(Context.getCurrent().getAgent(addID));
				env = Context.getCurrent().createEnvelope(stored, cc);
			} catch (AgentNotKnownException e1) {
				return Response.status(Status.NOT_FOUND).entity("Agent not found.").build();
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't add member!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			service.storeEnvelope(env, groupAgent);
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
		 * @param groupName Name of the group
		 * @param userName Name of the user
		 * @return Returns a Response
		 */
		@DELETE
		@Path("/{name}/member/{user}")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Groupmember removed."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_NOT_FOUND,
								message = "Agent does not exist.") })
		@ApiOperation(
				value = "Remove Group Member",
				notes = "Removes a member from a group.")
		public Response removeGroupMember(@PathParam("name") String groupName, @PathParam("user") String userName) {
			Envelope env = null;
			GroupAgent groupAgent = null;
			try {
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
			service.storeEnvelope(env, groupAgent);
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
	}
	
	
	@Path("/addressbook") // this is the root resource
	@Api(
			value = "Address Book Resource")
	@SwaggerDefinition(
			info = @Info(
					title = "laspeer Contact Service",
					version = "0.1",
					description = "A las2peer Contact Service for managing your contacts and groups.",
					termsOfService = "",
					contact = @Contact(
							name = "Alexander Neumann",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service",
							email = "neumann@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class AddressBookResource {
		ContactService service = (ContactService) Context.getCurrent().getService();

		@POST
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(
				value = "Add to Address Book",
				notes = "Add yourself to the address book.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Added"),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems or already in list.") })
		public Response addToAddressBook() {
			Agent owner = Context.getCurrent().getMainAgent();
			String identifier = address_prefix;
			Envelope env = null;
			boolean added = false;
			try {
				try {
					Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
					ContactContainer cc = (ContactContainer) stored.getContent();
					added = cc.addContact(owner.getId());
					env = Context.getCurrent().createUnencryptedEnvelope(stored, cc);
				} catch (ArtifactNotFoundException ex) {
					ContactContainer cc = new ContactContainer();
					added = cc.addContact(owner.getId());
					env = Context.getCurrent().createUnencryptedEnvelope(identifier, cc);
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			service.storeEnvelope(env, Context.getCurrent().getLocalNode().getAnonymous());
			if (added)
				return Response.status(Status.OK).entity("Added to addressbook.").build();
			else
				return Response.status(Status.BAD_REQUEST).entity("Already in list.").build();
		}

		@DELETE
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(
				value = "Remove from Address Book",
				notes = "Removes yourself from the address book.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Removed from address book."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems or you were not in the list.") })
		public Response removeFromAddressBook() {
			String identifier = address_prefix;
			Envelope env = null;
			boolean deleted = false;
			try {
				try {
					Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
					ContactContainer cc = (ContactContainer) stored.getContent();
					long userID = Context.getCurrent().getMainAgent().getId();
					deleted = cc.removeContact(userID);
					env = Context.getCurrent().createUnencryptedEnvelope(stored, cc);
				} catch (ArtifactNotFoundException ex) {
					ContactContainer cc = new ContactContainer();
					env = Context.getCurrent().createUnencryptedEnvelope(identifier, cc);
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
				return Response.status(Status.OK).entity("Could not be removed from list.").build();
			}
			if (deleted) {
				service.storeEnvelope(env, Context.getCurrent().getLocalNode().getAnonymous());
				return Response.status(Status.OK).entity("Removed from list.").build();
			} else {
				service.storeEnvelope(env, Context.getCurrent().getLocalNode().getAnonymous());
				return Response.status(Status.NOT_FOUND).entity("You were not in the list.").build();
			}
		}

		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(
				value = "Get Address Book",
				notes = "Get all contacts from the address book.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Contacts received."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Storage problems.") })
		public Response getAddressBook() {
			String identifier = address_prefix;
			JSONObject result = new JSONObject();
			try {
				try {
					Envelope stored = Context.getCurrent().fetchEnvelope(identifier);
					ContactContainer cc = (ContactContainer) stored.getContent();
					HashSet<Long> list = cc.getUserList();
					UserAgent user;
					for (Long l : list) {
						try {
							user = (UserAgent) Context.getCurrent().getAgent(l);
							result.put("" + user.getId(), user.getLoginName());
						} catch (AgentNotKnownException e1) {
							// Skip unknown agents
							e1.printStackTrace();
						}
					}
					return Response.status(Status.OK).entity(result).build();
				} catch (ArtifactNotFoundException ex) {
					Envelope env = null;
					ContactContainer cc = new ContactContainer();
					env = Context.getCurrent().createUnencryptedEnvelope(identifier, cc);
					service.storeEnvelope(env, Context.getCurrent().getLocalNode().getAnonymous());
					return Response.status(Status.OK).entity(result).build();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
			}
			return Response.status(Status.BAD_REQUEST).entity("Could not get any contacts.").build();
		}
	}
	
	@Path("/user") // this is the root resource
	@Api(
			value = "User Resource")
	@SwaggerDefinition(
			info = @Info(
					title = "laspeer Contact Service",
					version = "0.1",
					description = "A las2peer Contact Service for managing your contacts and groups.",
					termsOfService = "",
					contact = @Contact(
							name = "Alexander Neumann",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service",
							email = "neumann@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class UserResource {
		ContactService service = (ContactService) Context.getCurrent().getService();
	
		// //////////////////////////////////////////////////////////////////////////////////////
		// RMI Calls
		// //////////////////////////////////////////////////////////////////////////////////////

		@POST
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Updated user information."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "RMI error or wrong json.") })
		@ApiOperation(
				value = "Update User Information",
				notes = "Updates the name and the userimage.")
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
				return Response.status(Status.BAD_REQUEST).entity("").build();
			}
			return Response.status(Status.OK).build();
		}

		@GET
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(
				value = "Get User Information",
				notes = "Returns the name and the user image.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Got user information."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "RMI error.") })
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
				return Response.status(Status.BAD_REQUEST).entity("").build();
			}
			return Response.status(Status.OK).entity(returnString).build();
		}

		@GET
		@Path("/{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(
				value = "Get User Information for Name",
				notes = "Returns the name and the user image for a given user.")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Got user information"),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "RMI error or user does not exist.") })
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
				return Response.status(Status.BAD_REQUEST).entity("").build();
			}
			return Response.status(Status.OK).entity(returnString).build();
		}
	}
	
	@Path("/permission") // this is the root resource
	@Api(
			value = "Permission Resource")
	@SwaggerDefinition(
			info = @Info(
					title = "laspeer Contact Service",
					version = "0.1",
					description = "A las2peer Contact Service for managing your contacts and groups.",
					termsOfService = "",
					contact = @Contact(
							name = "Alexander Neumann",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service",
							email = "neumann@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class PermissionResource {
		ContactService service = (ContactService) Context.getCurrent().getService();
		
		@GET
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(
				value = "Get User Permission",
				notes = "Returns a field of the user's permissions")
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Got user permission."),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "RMI error.") })
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
				return Response.status(Status.BAD_REQUEST).entity("").build();
			}
			return Response.status(Status.OK).entity(returnString).build();
		}

		/**
		 * Updates the user information (firstName, lastName, userImage)
		 * 
		 * @param content JSON string containing the firstName, lastName and the userImage.
		 * @return Response
		 */
		@POST
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Updated permissions"),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "RMI error or wrong json.") })
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
				return Response.status(Status.BAD_REQUEST).entity("").build();
			}
			return Response.status(Status.OK).entity("").build();
		}
	}
	
	@Path("/name") // this is the root resource
	public static class NameResource {
		ContactService service = (ContactService) Context.getCurrent().getService();
		/**
		 * Function to get the login name of an agent
		 * 
		 * @param id The id of the agent
		 * @return The login name
		 */
		@GET
		@Path("/{id}")
		public Response getName(@PathParam("id") String id) {
	
			long agentid = Long.parseLong(id);
			try {
				UserAgent user = (UserAgent) Context.getCurrent().getAgent(agentid);
				String name = user.getLoginName();
				return Response.status(Status.OK).entity(name).build();
			} catch (AgentNotKnownException e) {
				String error = "Agent not found";
				return Response.status(Status.NOT_FOUND).entity(error).build();
			}
		}
	}
	
	/**
	 * Envelope helper method for storing an envelope.
	 * 
	 * @param env Envelope.
	 */
	private void storeEnvelope(Envelope env) {
		try {
			Context.getCurrent().storeEnvelope(env);
		} catch (StorageException e) {

			e.printStackTrace();
		}
	}

	/**
	 * Envelope helper method for storing an envelope.
	 * 
	 * @param env Envelope.
	 * @param owner Agent who owns the envelope.
	 */
	private void storeEnvelope(Envelope env, Agent owner) {
		try {
			Context.getCurrent().storeEnvelope(env, owner);
		} catch (StorageException e) {

			e.printStackTrace();
		}
	}
}

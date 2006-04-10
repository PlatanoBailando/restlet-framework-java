/*
 * Copyright 2005-2006 J�r�me LOUVEL
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * http://www.opensource.org/licenses/cddl1.txt
 * If applicable, add the following below this CDDL
 * HEADER, with the fields enclosed by brackets "[]"
 * replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet;

import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.AbstractChainlet;
import org.restlet.RestletCall;
import org.restlet.component.RestletContainer;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.ChallengeSchemes;
import org.restlet.data.SecurityData;
import org.restlet.data.Statuses;

import com.noelios.restlet.impl.ChallengeRequestImpl;
import com.noelios.restlet.util.Base64;

/**
 * Chainlet guarding the access to another Restlet, Chainlet or Maplet<br/>
 * Currently only supports the HTTP basic authentication scheme and a custom schemes (based on cookies, query params or IP address for example).
 * @see <a href="http://www.restlet.org/tutorial#part09">Tutorial: Guarding access to sensitive resources</a>
 */
public abstract class GuardChainlet extends AbstractChainlet
{
	/** Indicates if the guard should attempt to authenticate the caller. */
	protected boolean authentication;
	
	/** Indicates if the guard should attempt to authorize the caller. */
	protected boolean authorization;
	
	/** The authentication scheme. */
	protected ChallengeScheme scheme;
	
	/** Obtain a suitable logger. */
   protected Logger logger;

   /** The authentication realm. */
   protected String realm;

   /**
    * Constructor.
    * If the authentication is not requested, the scheme and realm parameters are not necessary (pass null instead).
    * @param container The parent container.
    * @param logName The log name to used in the logging.properties file.
    * @param authentication Indicates if the guard should attempt to authenticate the caller.
    * @param scheme The authentication scheme to use. 
    * @param realm The authentication realm.
    * @param authorization Indicates if the guard should attempt to authorize the caller.
    */
   public GuardChainlet(RestletContainer container, String logName, boolean authentication, ChallengeScheme scheme, String realm, boolean authorization)
   {
      super(container);

      this.logger = Logger.getLogger(logName);
      this.authentication = authentication;
      
      if(this.authentication && (scheme == null))
      {
      	throw new IllegalArgumentException("Please specify a challenge scheme. Use the 'None' challenge if no authentication is required.");
      }
      else
      {
      	this.scheme = scheme;
         this.realm = realm;
         this.authorization = authorization;
      }
   }

   /**
    * Handles a call to a resource or a set of resources.
    * @param call The call to handle.
    */
   public void handle(RestletCall call)
   {
   	if(this.authentication)
   	{
   		authenticate(call);
   	}

      if(!this.authorization || authorize(call))
      {
      	accept(call);
      }
      else
      {
         reject(call);
      }
   }

   /**
    * Attempts to authenticate the caller.
    * By default, it tries to interpret the challenge response using the Chainlet's challenge scheme.
    * Subclasses could implement different authentication mechanisms, for example based on IP address or on
    * a session cookie.<br/>
    * The result of a successful authentication is the update of the call's security data: login, password properties.
    * Subclasses could also set the additional role property.<br/>
    * If you know that the caller has already been authenticated, you should set the challenge scheme to NONE
    * in the constructor to silently skip this step. 
    * @param call The call to authenticate.
    */
   public void authenticate(RestletCall call)
   {
      SecurityData security = call.getSecurity();
      ChallengeResponse resp = security.getChallengeResponse();

      if(this.scheme.equals(ChallengeSchemes.HTTP_BASIC))
      {
         if(resp == null)
         {
            // Authentication failed, no challenge response provided, maybe first authentication attempt 
            logger.log(Level.INFO, "Authentication failed: no challenge response provided.");
         }
         else if(resp.getScheme().equals(ChallengeSchemes.HTTP_BASIC))
	      {
	         try
	         {
	            String credentials = new String(Base64.decode(resp.getCredentials()), "US-ASCII");
	            int separator = credentials.indexOf(':');
	
	            if(separator == -1)
	            {
	               // Log the blocking
	               logger.warning("Invalid credentials given by client with IP: " + call.getClientAddress());
	            }
	            else
	            {
	               security.setLogin(credentials.substring(0, separator));
	               security.setPassword(credentials.substring(separator + 1));
	
	               // Log the authentication result
	               logger.info("Basic HTTP authentication succeeded: login=" + security.getLogin() + ", password=" + security.getPassword() + ".");
	            }
	         }
	         catch(UnsupportedEncodingException e)
	         {
	            logger.log(Level.WARNING, "Unsupported encoding error", e);
	         }
	
	      }
	      else
	      {
	         // Authentication mechanism not supported
	         logger.log(Level.WARNING, "Authentication failed: invalid authentication mechanism used: " + resp.getScheme().getName() + " instead of :" + this.scheme.getName() + ".");
	      }
      }
      else
      {
         // Authentication failed, scheme not supported
         logger.log(Level.WARNING, "Authentication failed: unsupported scheme used: " + this.scheme.getName() + ". Please override the authenticate method.");
      }
   }
   
   /**
    * Indicates if the call is authorized to pass through the Guard Chainlet.
    * At this point the caller should be authenticated and the security data should contain a valid login,
    * password and optionnaly a role name.<br/>
    * The application should take care of the authorization logic, based on custom criteria such as
    * checking whether the current user has the proper role or access rights.<br/>
    * By default, no call is authorized and subclasses requiring authorization must override this method.
    * @param call The current call.
    * @return True if the given credentials authorize access to the attached Restlet.
    */
   protected boolean authorize(RestletCall call)
   {
      return false;
   }
   
   /**
    * Accepts the call.
    * By default, invokes the attached Restlet.
    * @param call The current call.
    */
   protected void accept(RestletCall call)
   {
   	// Invoke the chained Restlet
      super.handle(call);
   }
   
   /**
    * Rejects the call call due to a failed authentication or authorization.
    * This can be overriden to change the defaut behavior, for example to display an error page.
    * By default, if authentication is required, the challenge method is invoked, otherwise the 
    * call status is set to CLIENT_ERROR_FORBIDDEN.
    * @param call The current call.
    */
   protected void reject(RestletCall call)
   {
      if(this.authentication)
      {
      	challenge(call);
      }
      else
      {
      	call.setStatus(Statuses.CLIENT_ERROR_FORBIDDEN);
      }
   }
   
   /**
    * Sends a challenge request the caller in order to receive a (new) challenge response with caller's credentials.
    * Application using a custom authentication should override this method in order to provide a useful
    * challenging mechanism, such as displaying a login page.
    * @param call The current call.
    */
   protected void challenge(RestletCall call)
   {
		if(this.scheme.equals(ChallengeSchemes.HTTP_BASIC))
		{
			call.setStatus(Statuses.CLIENT_ERROR_UNAUTHORIZED);
			call.getSecurity().setChallengeRequest(new ChallengeRequestImpl(this.scheme, this.realm));
		}
		else
		{
         logger.log(Level.WARNING, "Unsupported challenging mechanism. Please override the challenge method or use a supported challenge scheme like HTTP Basic.");
		}
   }

}

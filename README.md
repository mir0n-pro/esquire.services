![Alt text](./favicon.ico)|Esquire Frameworks™ 2.0|
|:-|:-|

Frameworks for organizing business entities in a tree, for any business or activity. 
The framework is targeting to cover traditional functionality for a Backoffice (sub)system: onboarding, 
user profile maintenance, permissions, authorization, and accounting, as an example of a business process.


## esquire.services
A set of Esquire microservices

## In process

After month of active development, Version 1.2.0 has arrived !!! 

This is just a foundation for further development.

2 services: bizTree and Gateway, working using REST Api.

On the frontend, we use Esquire Explorer, a Node.js Angular application. See the [corresponding repository](https://github.com/mir0n-pro/esquire.explorer) for details.

The entity structure is fine for now; modifications are expected as I start working in detail with authorization and permissions.

Authentication drafted up to "OK-for-now" state.

Authorization is set to read-only for now.

Esquire Observability Stack completed, described here ( [./doc/Esquire.ObservabilityStack.md](./doc/Esquire.ObservabilityStack.md) ).


The bizTree, the main backend service, is going to be divided into 3 parts:
  - bizTree itself
  - enyMan (**En**tit**y** **Man**ager)
  - pacMan (**P**ersonal **Ac**count **Man**ager)

and eventually a 4th one:
  - lateron : or keyMan (**Key**cloak) **Man**ager) helping "enyMay" with Keycloak integration

The "lateron" service will use messaging for communication, not a REST API.

The next phase would be implementing basic functionality for entities creation and maintenance, biz tree modifications, permissions, and authorization.

After that: Synchronization signals between services.

Then: "bizTree" would use an embedded SQL DB instead of an Esquire DB; the tree will be loaded and updated in real time based on entity relationships.

Then, "pacMan" going away from JPA, it will start working with an embedded object DB, every account is in its own memory and (virtual) thread.
Also, "pacMan" will have alternative to REST facade to make incoming trafic more aggressive.

That is the next horizon. At this point, Esquire will be published on one or another cloud vendor, for demonstration as well as for stress testing.

Well, in the middle, I would need to find a mood to write documentation, similar to the Observability Stack doc.
 - Component Model
 - Permissions and Authorization 
 - Messaging structure
 - APIs
 - &c,&c
 

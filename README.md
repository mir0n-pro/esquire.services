| ![Alt text](./favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|-------------------------|

Frameworks for organizing business entities in a tree, for any business or activity. The framework aims to cover traditional functionality for a Backoffice (sub)system, including onboarding, user profile maintenance, permissions, authorization, and accounting, as a business process example.

## esquire.services

A set of Esquire microservices

## In process

**Current version: 1.2.1** Backend services are split into 4 parts with isolated functional domains. Access Permission (authentication + authorization) feature introduced.

Backend and Gateway services, working using REST Api.

On the frontend, we use Esquire Explorer, a Node.js Angular application. See the [corresponding repository](https://github.com/mir0n-pro/esquire.explorer) for details.

The entity structure is fine for now

Authentication drafted up to "OK-for-now" state. [Keycloak IAM solution](https://www.keycloak.org/) is in use.

Authorization is set to read-only for now.

Esquire Observability Stack completed, described here [ObservabilityStack.md](./doc/Esquire.ObservabilityStack.md).

Set of Backend Esquire Services includes:

-   bizTree: a component responsible for entity tree navigation
-   enyMan: **En**tit**y** **Man**ager: to cover all functionality related to entities (except accounts)
-   keySmith: Access Profiles and **Key**cloak integration
-   pacMan: **P**ersonal **Ac**count **Man**ager: account, and its activity, the place where actual Business Logic is implemented. All other backend services are made to support the pacMan functionality.

The next phase would be implementing basic functionality for entity creation and maintenance, biz tree modifications, permissions, and authorization.

After that: Synchronization signals between services using asynchronous messaging.

Then, "bizTree" would use an embedded SQL DB instead of an Esquire DB; the tree will be loaded and updated in real time based on entity relationships.

Then, "pacMan" going away from JPA, it will start working with an embedded object DB, every account is in its own memory and (virtual) thread. Also, "pacMan" will have an alternative to REST facade to make incoming traffic more aggressive.

That is the next horizon. At this point, Esquire will be published on one or another cloud vendor, for demonstration as well as for stress testing.

Well, in the middle, I would need to find a mood to write documentation, similar to the Observability Stack doc.

-   Component Model
-   Permissions and Authorization
-   Messaging structure
-   APIs
-   \&c,&c

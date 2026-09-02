# Esquire frameworks (tm) -- T4 probe, NOT part of any deployment.
#
# Cognito puts a custom attribute in the ID token and NOT in the access token. Esquire's
# JwtClaimsExtractionFilter validates the ACCESS token and refuses one without esq_uid and
# esq_rootpath, so without something like this a Cognito access token cannot pass the filter.
#
# V2_0 of the trigger is the version that can write into the access token at all; V1_0 can
# only write into the ID token. V2_0 needs the ESSENTIALS or PLUS user-pool tier.
def handler(event, context):
    attrs = event["request"]["userAttributes"]
    event["response"] = {
        "claimsAndScopeOverrideDetails": {
            "accessTokenGeneration": {
                "claimsToAddOrOverride": {
                    "esq_uid": attrs.get("custom:esq_uid", ""),
                    "esq_rootpath": attrs.get("custom:esq_rootpath", ""),
                    # The third claim Esquire's filter demands is realm_access.roles -- a NESTED
                    # object. Cognito's own answer is the flat cognito:groups, so this tests
                    # whether a trigger can emit the KeyCloak SHAPE, not just a flat value.
                    "realm_access": {"roles": event["request"].get("groupConfiguration", {}).get("groupsToOverride", [])}
                }
            }
        }
    }
    return event

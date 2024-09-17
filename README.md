# Jenkins Folder Credentials Importer

This Jenkins plugin allows importing credentials into a folder from an upstream source.  
For example, it can be used to import `SYSTEM` scoped (i.e. inaccessible to jobs) credentials from a specific domain in the system store into a domain in the folder store with `GLOBAL` scope so that they are accessible only to jobs within that folder. Like so e.g. all credentials can be configured in a [JCasC](https://www.jenkins.io/projects/jcasc/) config and then be imported into folders.

### Usage

This plugin can only be configured as a folder property with the [Job DSL](https://plugins.jenkins.io/job-dsl/) (or similar) plugin.

For Job DSL:

```
folder {
  properties {
    importCredentials {
      // Whether all other credentials that weren't imported should be removed.
      // Default: false.
      clear(<bool>)

      // Whether credentials should always be re-imported when this property is saved, even if config did not change.
      // Default: true.
      update(<bool>)

      imports {
        // Block can be specified multiple times
        from {
          source {
            // One or more ANT glob patterns for matching credentials by id.
            // Required.
            ids([<pattern>...])

            // One or more ANT glob patterns for matching credentials by domain.
            // Optional.
            domains([<pattern>...])

            // One or more URIs for matching credentials by their domain's specifications.
            // Note that if a domain does not have any specifications its credentials will always match, regardless of the URIs specified here.
            // Optional.
            uris([<uri>...])

            // One or more strings for matching credentials by scope.
            // Valid values: "SYSTEM", "GLOBAL", "USER".
            // Default: "GLOBAL", "USER".
            scopes([<scope>...])

            // One or more strings for matching credentials by source store.
            // Valid values: "SYSTEM" (= system store only), "JOB" (= all stores available to job doing the configuration).
            // Default: "SYSTEM", "JOB".
            sources([<source>...])

            // Whether the import should fail when no credentials were matched.
            required(<bool>)
          }

          // Optional block
          to {
            // If specified the imported credentials' scope is set to this value.
            // Optional.
            scope(<scope>)

            // One or more ANT glob patterns specifying which domains of the imported credentials should be copied into the folder store.
            // Optional.
            copiedDomains([<pattern>...])

            // Domain into which the credentials should be imported if copiedDomains was not specified or did not take effect. The domain is created if it does not exist yet.
            // Some other domain specification properties are left out here and can be looked up in the Job DSL domain specification documentation.
            // Optional.
            defaultDomain {
              name(<string>)
            }
          }
        }
      }
    }
  }
}
```

Using this property requires Job DSL jobs to be running with an authentication other than `SYSTEM` (see e.g. [Authorize Project](https://plugins.jenkins.io/authorize-project/)).

Note that the required permissions depend on the configuration. For example, importing credentials from the `SYSTEM` scope requires the `ADMINISTER` permission, and when using `copiedDomains` or `defaultDomain` the `MANAGE_DOMAINS` permission is required. In any case the credentials `CREATE` and `UPDATE` permissions are also required.

### Example

This example imports `SYSTEM` scoped credentials under the 'Source' domain from the system store into the folder store under the 'Target' domain with `GLOBAL` scope.

```
importCredentials {
  clear(true)
  imports {
    from {
      source {
        ids(['*'])
        domains(['Source'])
        sources(['SYSTEM'])
        scopes(['SYSTEM'])
      }
      to {
        scope('GLOBAL')
        defaultDomain {
          name('Target')
        }
      }
    }
  }
}
```

### Development

Starting a development Jenkins instance with this plugin: `mvn hpi:run`

Building the plugin: `mvn package`

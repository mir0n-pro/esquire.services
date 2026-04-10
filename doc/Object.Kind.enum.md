# Object.Kind.enum

### Object Kind what that for?
todo

### There is an object classification in the system 
- real entities
- links to real entities
- system tree nodes
- special system objects
- custom extensions
- no rule without exception

#### Links: there are odd and even numbers.
- an odd kind describes a node linked to the actual node with even kind (kind/2)*2
- an even kind describes the actual object

#### Special/Exceptional kinds:

|       |    ||
|:------|:---|:---|
|       |999|"More" UI hint|
||-1|Not a kind/unknown|

#### Objects and entities those are in the biz tree (0..600)

||    ||
|:------|:---|:---|
|| 0  | System Root |
||    ||
| 2..18 |    |**System Folder Tree Nodes**
|       | 2  | Sys-admins|
|       | 4  |All admins|
|       | 6  |All accounts|
|       | 8  |All clients|
|       | 10 |All merchants|
|30..48|    |**User Entities**|
|       | 30 |SysAdmin|
|       | 32 |Admin|
|       | 34 |SysAdmin|
|       | 36 |Client|
|50..68|    |**Account Entites**|
|       | 50 |Client Account|
|       | 52 |Merchant Account|
|       | 54 |Paper Account|

#### Permission/Roles kinds (970..986)

||     ||
|:------|:----|:---|
|970..986|     |**System Roles**| 
|| 980 |Admin Entity functions|
|| 982 |Tools, Tools available| 
|| 984 |Apps, Applications permitted (reserved)|
|| 986 |Reports, Report available (reserved)|

#### User profile things (988..998)

|          |     |                  |
|:---------|:----|:-----------------|
| 988..998 || **Sub-entity kinds** |
|          |988|Postal Address|
|          |992|Primary Person|
|          |990|Biz Address|
|          |994|Joint Person(reserved)|
|          |996| Seconady Person(reserved)|
|          |998|User Access Profile|

### Custom kinds: ( 1000..2000)

|            |     |                  |
|:-----------|:----|:-----------------|
| 1000..2000 || **Reserved for custom extensions**|
|            |1000||accounting-deposit|
|            |1002||accounting-withdrawal|
|            |1004||accounting-transfer|




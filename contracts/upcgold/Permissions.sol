pragma solidity ^0.6.2;
pragma experimental ABIEncoderV2;


contract Permissions { 
 
    bytes32 private currentGuid = "96320a6545aa4d0d29427a9929be3f0a";
    struct ClientMode {
        address client;
        uint timestamp;
        bytes32 hash;
    }
 
 
    event DebugEvent (
        address currentStaker
    );    

 
    mapping(bytes32 => ClientMode)     public clients;
    ClientMode[] public addressToClient;
    
    bytes32[] public spawnedGuids;

    address public owner;
    uint clientCount = 0;
    
    //use block100 modifier to control access to the payout function
    modifier ownerGuid() {
        require( owner == msg.sender , "Error: Only owner can see the guid");
        _;
    }
    
    
    //use block100 modifier to control access to the payout function
    modifier validateCode(bytes32 toValidate) {
        uint codeTimestamp = clients[toValidate].timestamp;
        require( codeTimestamp != 0 , "This is not a valid client qr code");
        _;
    }


    constructor () public {
        owner = msg.sender;
    }
    
    
    function setGuid(bytes32 newGuid) public ownerGuid returns(bytes32) {
        currentGuid = newGuid;
        return currentGuid;
    }


    function getSpawnedGuids() public ownerGuid view returns(bytes32[] memory) {
        return spawnedGuids;
    }

    function getGuid() public ownerGuid view returns(bytes32) {
        return currentGuid;
    }
        
        
    //this function is called from the android app when the app detects the register protocol in the upc
    //the person scanning is a client who is holding media with the scannable embedded, and was specifically given to them
    function addClient(bytes32 registerHash) public validateCode(registerHash) {
        
        bool doAdd = true;
        //also validate that user is not already in clients array
        for(uint i = 0; i< addressToClient.length; i++) {
            if(addressToClient[i].client == msg.sender) {
                doAdd = false;
            }
        }
        
        require(doAdd == true , "User is already in client mode");
        
        if(doAdd) {
            if(clients[registerHash].client == address(0x0)) {
                clientCount++;
                clients[registerHash].client = msg.sender;
                ClientMode memory cm = ClientMode(msg.sender, clients[registerHash].timestamp, clients[registerHash].hash);
                addressToClient.push(cm);
            }
        }

    }
            
        
    //will return the hash of the user in the slot, or return zero hash
    function findClient(address addy) public view returns(ClientMode memory) {
        
        for(uint i = 0; i< addressToClient.length; i++) {
            if(addressToClient[i].client == addy) {
                return addressToClient[i];
            }
        }
        
        ClientMode memory cm = ClientMode(address(0x0), 0, '' );
        return cm;
    }
    
    
    function printBlank() public ownerGuid {
        ClientMode memory cm = ClientMode(address(0x0), now, currentGuid);
        spawnedGuids.push(currentGuid);
        clients[currentGuid] = cm;
        currentGuid = sha256(abi.encodePacked(currentGuid));
    }

}

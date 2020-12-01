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

    uint private blanksToPrint = 10;
 
    mapping(bytes32 => ClientMode)     public clients;
    ClientMode[] public addressToClient;
    
    bytes32[] public spawnedGuids;
    bytes32[] private lastGuidBatch;

    address public owner;
    uint clientCount = 0;
    uint public blanksPrinted = 0;

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
   
    function setBlanksToPrint(uint blanks) public ownerGuid {
        blanksToPrint = blanks;
    } 
    
    function setGuid(bytes32 newGuid) public ownerGuid returns(bytes32) {
        currentGuid = newGuid;
        return currentGuid;
    }


    function getSpawnedGuids() public ownerGuid view returns(bytes32[] memory) {
        return spawnedGuids;
    }
    
    function getLastGuidBatch() public ownerGuid view returns(bytes32[] memory) {
        return lastGuidBatch;
    }

    function getGuid() public ownerGuid view returns(bytes32) {
        return currentGuid;
    }
    
    function getGuidRange(uint start, uint end) public ownerGuid view returns(bytes32[] memory) {
        
        require(start <= end , "Getting range and start must be <= end");
        uint currentArrayCount = 0;
        uint arrSize = end - start;
        if(arrSize == 0) {
            arrSize = 1;
        }
        
        bytes32[] memory toRet = new bytes32[](arrSize);

        for(uint i = start; i< end; i++) {
            toRet[currentArrayCount] = spawnedGuids[i];
            currentArrayCount++;
        }
        return toRet;
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
    
    
    function printBlanks() public ownerGuid returns(bytes32[] memory){
        bytes32[] memory toRet = new bytes32[](blanksToPrint);
        for(uint i = 0; i< blanksToPrint; i++) {
            toRet[i] = printBlank();
            blanksPrinted++;
        }
        lastGuidBatch = toRet;
        return toRet;
    }
    
    
    function printBlank() private returns(bytes32) {
        ClientMode memory cm = ClientMode(address(0x0), now, currentGuid);
        spawnedGuids.push(currentGuid);
        clients[currentGuid] = cm;
        currentGuid = sha256(abi.encodePacked(currentGuid));
        return currentGuid;
    }

}

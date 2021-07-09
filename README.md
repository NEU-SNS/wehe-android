# About the Application

This app helps user run a simple test on their Android devices to find out if their ISP is throttling internet for some app or not. Please refer https://dd.meddle.mobi/td_details.html fpr detailed understanding of the test.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/mobi.meddle.wehe/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=mobi.meddle.wehe)

# Documentation of the API

There are three different connections that the client can make to the server.

**Analyzer (port 56565 or port 56566):** used for the client to ask for the default setting, send POST request for differentiation analysis and GET analysis result.

**Side Channel (port 55555 or port 55556):** used for the client to ask for permission for replay and send meta info (i.e., carrier name, GPS locations etc.) to the server.

The ports ending are 6 is for the encrypted version of the test

**Replay port (port 80, port 443, etc.):** used for the client and server to replay recorded trace (e.g. the Youtube traffic).

The DPI functionality needs modification on all of the three above connections.

## Section 1
This section details the communication between Analyzer during the DPI analysis.

Once a differentiation test finishes and differentiation is detected (i.e., ‘Differentiation Detected’ showing on the phone), button 1 pops up for ‘Get DPI test result’.

If the user clicks button 1, send a GET request to Analyzer, with the following parameters:
```
{
‘command’: ‘DPIrule’,
‘userID’ : randomId generated for that instance of the application,
‘carrierName’ : the ISP that the client is connected to, set to WiFi if the phone is using WiFi connection,
‘replayName’.
}
```

Following is an example request here:
```bash
http://54.83.152.150:56565/Results?command=DPIrule&userID=Ue2Mlc5IAT&carrierName=ATT&replayName=youtube
```

The Analyzer response is a dictionary in json format, and let’s assume you have loaded it into a dictionary called result. It contains a key ‘success’, and if result[‘success’]=True, then there is DPI result for this client (i.e., he has done a test before), otherwise it is set to False.

When `result[‘success’]=True`, you can then read the results from `result[‘response’]`, which has ‘timestamp’ (when the test was done), ‘numTests’ (how many tests/replays was performed for this DPI test) and ‘DPIrule’.

When `result[‘success’]=False`, there should be an error message in `result[‘error’]`, if it is ‘No result found’, then it means this client has never done a DPI test before, we now suggest him to run a test (e.g. button 2 for ‘Ask for DPI test’, and big warning about how many data and time it needs).

If user clicks button 2, send a GET request to Analyzer, with the following parameters: ‘command’ = ‘DPIanalysis’, userID, carrierName, replayName, historyCount (the same value from the tests that identified differentiation, and it stays the same during the whole DPI analysis process), testID (starting from 2, because 0 is the original replay and 1 is the control/random test, and it increments by 1 each time), testedLeft (default -1), testedRight (default -1), diff (default ‘F’).

testedLeft and testedRight are used to inform server which bytes were inverted for the *previous* test, for example, if the client did a test with region from byte 1 to byte 10 inverted, testedLeft will be set to 1 and testedRight set to 10. diff is set to ‘T’ if the previous test shows differentiation (i.e., different than the original replay).

The Analyzer response is a dictionary in json format, and let’s assume you have loaded it into a dictionary called result. It contains a key ‘success’, and if result[‘success’]=True, then either proceed the test or DPI test finishes, otherwise it is set to False.

When result[‘success’]=True :

If ‘DPIrule’ in result[‘response’], DPI test finishes, and you can read the result from result[‘response’][‘DPIrule’] and result[‘response’][‘numTests’]

Else the DPI test hasn’t finished yet, read the information for next test from result[‘response’]. They are result[‘response’][‘testPacket’], result[‘response’]

[‘testLeft’] and result[‘response’][‘testRight’]. ‘testPacket’ is which packet to modify for the next test, testLeft and testRight determine which bytes to invert in that packet.

For example, if testPacket = ‘C_1’ (i.e., the first character is either C or S, indicates whether the modification is on the client side or the server side, the integer is then the packet number on that side), testLeft = 10 and testRight = 20, you need to modify the first client packet, and invert the bytes from 10 to 20.

When result[‘success’]=False, read from result[‘error’] for the error message.
There is a button ‘Reset DPI test’, once clicked, the client sends a GET request with the following parameters: ‘command’ = ‘DPIreset’, userID, carrierName, replayName. The server will reset the tests for the client.

## Section 2

This section details how to replay with content modified/inverted during the binary search process of the DPI analysis.

Assume now you have received a response from the Analyzer, which contains the next test needed. As mentioned before, side = testPacket.split(‘_’)[0] (either ‘C’ or ‘S’) and packetnum =  testPacket.split(‘_’)[1].

side = ‘C’, modification on the client side, and assume the packet number is 2. Thus you need to modify the second packet before sending it out to the server. The code snippet below should explain the high-level idea: invert the bytes specified in the server response. Note that here we are sending traffic to the Replay port, and the modification is done on the actually replay payload.

side = ‘S’, again assuming the packet number is 2, testLeft = 10, testRight = 20.

The client can not modify payload sent from the server, thus instead, the client needs to inform the server what to change for this test. After the ‘identifying’ step (the very initial communication with the Side Channel where the client sends userID, testID, replayName etc.) with the Side Channel.

The client should send a string in json format of three elements :

[pacNum, action, region], e.g., using Python’s json.dumps([pacNum, action, region]), since the server loads the data using json.loads(data).

Where pacNum specifies which packet needs to be changed on the server side (e.g. 2 means the second packet from the server), action should always be ‘replaceI’, and region is a tuple (testLeft, testRight) means the bytes from testLeft to testRight need to be inverted.

If no change needed on the server side (e.g. during normal tests or client side modification), just send [-1, "null", "null"].

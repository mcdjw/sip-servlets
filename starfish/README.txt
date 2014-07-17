
CALL
REQUEST:

"call_control": "call",
"request_id": "123XYZ",
"origin": "tel:+18164388687",
"destination": "tel:+18165057120"


RESPONSE:
"event": "call-connected",
"request_id": "123XYZ",
"status": 200,
"reason": OK


Disconnect
"call_control": "disconnect",
"request_id": "123XYZ"


"event": "call_terminated",
"request_id": "123XYZ",
"status": 200,
"reason": OK

Transfer
"content": {
   "call_control": "transfer",
   "request_id": "123XYZ",
   "endpoint": "tel:+18166623395"
}
"content": {
  "event": "call_transferred",
"request_id": "123XYZ",
   "status": 200,
   "reason": OK
}


Hold
"content": {
   "call_control": "hold",
   "request_id": "123XYZ",
}
"content": {
  "event": "call_held",
"request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
Place an existing call on hold


Retrieve Call
"content": {
   "call-control": "retrieve",
   "request_id": "123XYZ",
}
"content": {
  "event": "call_retrieved",
"request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
Retrieve an existing call from hold
Dial Digits
"content": {
   "call_control": "dial",
   "request_id": "123XYZ",
   "digits": "12345#"
}
"content": {
   "event": "call_retrieved",
   "request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
Send digits dialed to server
mute
"content": {
   "call_control": "mute",
   "request_id": "123XYZ",
}
"content": {
  "event": "call_muted",
"request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
mute a call-control call
un-mute
"content": {
   "call-control": "un_mute",
   "request_id": "123XYZ",
}
"content": {
  "event": "call_un_muted",
"request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
un-mute a call-control call
redirect
"content": {
   "call_control": "transfer",
   "request_id": "123XYZ",
   "endpoint": "tel:+18166623395"
}
"content": {
   "event": "call_transferred",
   "request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
change destination of an incoming call



accept
"content": {
   "call_control": "accept",
   "request_id": "123XYZ",
   "endpoint": "tel:+18166623395"
}
"content": {
   "event": "call_accepted",
   "request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
accept an incoming call from an offering endpoint
reject
"content": {
   "call_control": "reject",
   "request_id": "123XYZ",
   "endpoint": "tel:+18166623395"
}
"content": {
  "event": "call_rejected",
"request_id": "123XYZ",
   "status": 200,
   "reason": OK
}
reject an incoming call from an offering endpoint



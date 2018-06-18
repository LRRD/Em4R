
#include "udpserver.h"
#include "operatorbase.h"
#include "encoderbase.h"
#include "messages.h"
#include "preferences.h"

#include <debug.h>

#include <Arduino.h>
#include <Ethernet.h>
#include <utility/util.h>
#include <IPAddress.h>

// The maximum size of a transmitted or received message.
#define	MSG_BUF_BYTES		256

// The interval at which we report status.
#define	STATUS_INTERVAL_MS	250

// The time after which we abandon a peer.
#define	PEER_TIMEOUT_MS		30000

// The maximum number of peers supported at once.
#define	MAX_PEERS		5

// Default network addresses.
static const uint8_t default_mac_address[6] = { 0xDE, 0xAD, 0xBE, 0xEF, 0xCA, 0xFE };
static const uint8_t default_ip_address[4] = { 10, 0, 8, 200 };
static const uint16_t default_udp_port = 40000;
static const uint16_t protocol_version = 1;

static void debug_packet(const uint8_t* buf_, size_t bytes_)
{
	DBG_ERR("%u bytes:", bytes_);
	char line[80];
	size_t byte = 0;
	while (byte < bytes_) {
		line[0] = '\0';
		for (size_t col = 0; (col < 4) && (byte < bytes_); col++) {
			char word[4];
			snprintf(word, sizeof(word), "%02X ", buf_[byte++]);
			strncat(line, word, sizeof(line));
			
		}
		DBG_ERR(line);
	}
}

struct UDPServer::Impl
{
	// Implement main-class methods.
	~Impl();
	Impl(Preferences& prefs_, OperatorBase* operator_base_, EncoderBase* encoder_base_);
	uint32_t ActiveMilliseconds();
	void Work();

	// The preferences database.
	Preferences& prefs;

	// The operator manager.
	OperatorBase* operator_base;

	// The encoder manager.
	EncoderBase* encoder_base;

	// The 16-bit system ID.
	uint16_t system_id;

	// Working message buffer.
	uint8_t* work;

	// The UDP packet handler.
	EthernetUDP udp;

	// Information on one peer (to which are are sending status).
	struct peer_info_t
	{
		peer_info_t();

		// true if this slot in the peer list is active; false otherwise.
		bool active;

		// The IP address and port of the peer.
		IPAddress ip;
		uint16_t port;

		// The time (millis) of the last valid message received from the peer.
		uint32_t last_rx_ms;

		// The time (millis) at which we last sent status to the peer.
		// If 0, the time has not yet been sent to the peer.
		uint32_t last_status_sent_ms;

		// The sequence number to go in the next status message.
		uint8_t tx_seq;

		// The sequence number from the last control message.
		uint8_t rx_seq;
	};

	// The peer list.
	peer_info_t peer_list[MAX_PEERS];

	// Process a control capsule from a received UDP message.
	// buf_ points to the header of the capsule to be processed.
	// packet_bytes_ is the number of bytes remaining in the entire message.
	// Returns the number of bytes processed at buf_, or 0 on error.
	int ProcessControlCapsule(uint8_t* buf_, int packet_bytes_);

	// Process a system control capsule from a received UDP message.
	// The peer address (UDP.remoteIP and UDP.remotePort) must be valid.
	// gcc_ refers to the capsule whose "cap" and "sccd" members are to be processed.
	// Returns true on success; false on failure.
	bool ProcessSystemControlCapsule(generic_control_capsule_t& gcc_);

	// Process an operator control capsule from a received UDP message.
	// gcc_ refers to the capsule whose "cap" and "occd" members are to be processed.
	// Returns true on success; false on failure.
	bool ProcessOperatorControlCapsule(generic_control_capsule_t& gcc_);
};

UDPServer::Impl::peer_info_t::peer_info_t() :
	active(false),
	port(0),
	last_rx_ms(0),
	last_status_sent_ms(0),
	tx_seq(0),
	rx_seq(0)
{
	ip[0] = ip[1] = ip[2] = ip[3] = 0;
}

UDPServer::Impl::~Impl()
{
	delete work;
}

UDPServer::Impl::Impl(Preferences& prefs_, OperatorBase* operator_base_, EncoderBase* encoder_base_) :
	prefs(prefs_),
	operator_base(operator_base_),
	encoder_base(encoder_base_),
	system_id(0),
	work((uint8_t*)new uint32_t[MSG_BUF_BYTES])
{
	// Look up the system identifier.
	system_id = prefs.GetUint16("sysid", 0);

	// Initialize the Ethernet subsystem.
        uint8_t mac[6];
        uint8_t ip[4];
        prefs.GetMACAddress(mac, "mac", default_mac_address);
        prefs.GetIPAddress(ip, "ip", default_ip_address);
	uint16_t port = prefs.GetUint16("port", default_udp_port);
        Ethernet.begin(mac, ip);

	// Begin listening on the command port.
	udp.begin(port);

	DBG_ERR("Listening on Ethernet (%02X:%02X:%02X:%02X:%02X:%02X) at %u.%u.%u.%u:%u",
		mac[0], mac[1], mac[2], mac[3], mac[4], mac[5], ip[0], ip[1], ip[2], ip[3], port);

}

int UDPServer::Impl::ProcessControlCapsule(uint8_t* buf_, int packet_bytes_)
{
	// Ignore messages too short to be legitimate control messages.
	if (packet_bytes_ < sizeof(capsule_header_t)) {
		DBG_ERR("  control capsule too short to be valid");
		return 0;
	}

	// Process the capsule header.
	generic_control_capsule_t& gcc = *(generic_control_capsule_t*)buf_;
	gcc.cap.magic = ntohs(gcc.cap.magic);

	// If the byte count extends beyond the remaining message, fail.
	if (gcc.cap.bytes_after > packet_bytes_) {
		DBG_ERR("  capsule byte count longer than message");
		return 0;
	}

	// If the byte count is too long for the longest capsule, fail.
	if ((gcc.cap.bytes_after + sizeof(gcc.cap)) > sizeof(gcc)) {
		DBG_ERR("  capsule byte count too long");
		return 0;
	}

	//DBG_ERR("MAGIC = %04X", gcc.cap.magic);

	// Process the capsule header.
	switch (gcc.cap.magic) {
	case SYSTEM_CONTROL_MAGIC:
		if (!ProcessSystemControlCapsule(gcc)) {
			return 0;
		}
		break;

	case OPERATOR_CONTROL_MAGIC:
		if (!ProcessOperatorControlCapsule(gcc)) {
			return 0;
		}
		break;
	
	default:
		DBG_ERR("  unrecognized capsule magic number (%04X); ignoring", gcc.cap.magic);
		break;
	}

	// Move past the capsule.
	return sizeof(gcc.cap) + gcc.cap.bytes_after;
}

bool UDPServer::Impl::ProcessSystemControlCapsule(generic_control_capsule_t& gcc_)
{
	// Figure out who sent us the message.
	IPAddress peer_ip = udp.remoteIP();
	uint16_t peer_port = (uint16_t)udp.remotePort();

	DBG_ERR("System control capsule from %u.%u.%u.%u:%u", peer_ip[0], peer_ip[1], peer_ip[2], peer_ip[3], peer_port);

	// Fix byte order.
	gcc_.sccd.ms = ntohl(gcc_.sccd.ms);

	// Check the capsule header.
	if (gcc_.cap.instance != protocol_version) {
		DBG_ERR("  bad message protocol version (%02X); ignoring", gcc_.cap.instance);
		return false;
	}
	if (gcc_.cap.bytes_after != sizeof(gcc_.sccd)) {
		DBG_ERR("  bad system control capsule byte count (%u); ignoring", gcc_.cap.bytes_after);
		return false;
	}
	
	// Search the list of peers to see if we already know about this one.
	// While we're at it, look for an available slot in case we need to add them.
	peer_info_t* this_peer = nullptr;
	bool any_active = false;
	int8_t available_slot = -1;
	for (int8_t zpeer = 0; zpeer < MAX_PEERS; zpeer++) {
		if (peer_list[zpeer].active && (peer_list[zpeer].ip == peer_ip) && (peer_list[zpeer].port == peer_port)) {
			this_peer = &peer_list[zpeer];
		}
		if (peer_list[zpeer].active) {
			any_active = true;
		} else if (available_slot < 0) {
			available_slot = zpeer;
		}
	}

	// If we found it, update its last-receive time.
	if (this_peer) {
		this_peer->last_rx_ms = millis();
	}

	// If we didn't find it in the peer list, add it.
	else {
		if (available_slot < 0) {
			// Peer table is full; can't add.
			return false;
		}
		this_peer = &peer_list[available_slot];
		this_peer->active = true;
		this_peer->ip = peer_ip;
		this_peer->port = peer_port;
		this_peer->last_status_sent_ms = 0;
		this_peer->last_rx_ms = millis();
		this_peer->tx_seq = millis() % 0xFF;

		DBG_ERR("UDP: New peer at %u.%u.%u.%u:%u", peer_ip[0], peer_ip[1], peer_ip[2], peer_ip[3], peer_port);
	}

	// Remember the sequence number.
	this_peer->rx_seq = gcc_.sccd.rx_seq;

	// TODO: command handling, if not SYSTEM_CONTROL_CMD_NOP.

	// NOTE: we could check the current time for consistency.
	return true;
}

bool UDPServer::Impl::ProcessOperatorControlCapsule(generic_control_capsule_t& gcc_)
{
	DBG_ERR("Operator control capsule for operator %u", gcc_.cap.instance);

	// Check the capsule header.
	if (gcc_.cap.bytes_after != sizeof(gcc_.occd)) {
		DBG_ERR("  bad operator control capsule byte count (%u); ignoring", gcc_.cap.bytes_after);
		return false;
	}
	if (!operator_base || (gcc_.cap.instance >= operator_base->OperatorCount())) {
		DBG_ERR("  unknown operator instance (%u), ignoring", gcc_.cap.instance);
		// This is not a full-message failure.
		return true;
	}

	// Fix byte order.
	gcc_.occd.time_to_achieve = ntohl(gcc_.occd.time_to_achieve);
	gcc_.occd.requested_value = ntohs(gcc_.occd.requested_value);

	// Call the processor method, if any.
	operator_base->Control(gcc_.cap.instance, gcc_.occd);
	return true;
}

uint32_t UDPServer::Impl::ActiveMilliseconds()
{
	return millis();
}

void UDPServer::Impl::Work()
{
	// See if there are any UDP packets available.
	int packet_bytes;
	while (0 != (packet_bytes = udp.parsePacket())) {
//		DBG_ERR("======================================== Got %u bytes", packet_bytes);

		// Read the message.
		if (packet_bytes > MSG_BUF_BYTES) {
			// Ignore.
			continue;
		}
		udp.read(work, packet_bytes);
//		debug_packet(work, (size_t)packet_bytes);
		
		// Process all valid capsules in the message.
		uint8_t* cap = work;
		while (cap < (work + packet_bytes)) {

			// Process the next capsule and fail on error.
			uint8_t processed_bytes = ProcessControlCapsule(cap, work + packet_bytes - cap);
			if (processed_bytes == 0) {
				break;
			}
//			DBG_ERR("Processed %u bytes of capsule", processed_bytes);

			// Adjust length and address based on what we just processed.
			cap += processed_bytes;
		}
	}

	// See if a peer has timed out.
	for (uint8_t zpeer = 0; zpeer < MAX_PEERS; zpeer++) {
		peer_info_t* peer = &peer_list[zpeer];
		if (peer->active && ((millis() - peer->last_rx_ms) > PEER_TIMEOUT_MS)) {
			DBG_ERR("UDP: Dropping peer at %u.%u.%u.%u:%u", peer->ip[0], peer->ip[1], peer->ip[2], peer->ip[3], peer->port);
			peer->active = false;
		}
	}

#if 1
	// See if we need to send status updates.
	for (uint8_t zpeer = 0; zpeer < MAX_PEERS; zpeer++) {
		peer_info_t* peer = &peer_list[zpeer];

		// If the peer is not active, or if we've sent status recently, skip it.
		if (!peer->active || (peer->last_status_sent_ms && ((millis() - peer->last_status_sent_ms) < STATUS_INTERVAL_MS))) {
			continue;
		}

		peer->last_status_sent_ms = millis();

		// Determine how much space is required.
		size_t bytes_required = sizeof(capsule_header_t) + sizeof(system_status_capsule_data_t);
//		DBG_ERR("need to send %u bytes", bytes_required);
		uint8_t operator_count = 0;
		if (operator_base) {
			operator_count = operator_base->OperatorCount();
			bytes_required += ((sizeof(capsule_header_t) + sizeof(operator_status_capsule_data_t)) * operator_count);
		}
		uint8_t encoder_count = 0;
		if (encoder_base) {
			encoder_count = encoder_base->EncoderCount();
			bytes_required += ((sizeof(capsule_header_t) + sizeof(encoder_status_capsule_data_t)) * encoder_count);
		}
		if (bytes_required <= MSG_BUF_BYTES) {

			uint8_t* next = work;

			// Set up the system status capsule.
			capsule_header_t* cap = (capsule_header_t*)next;
			cap->bytes_after = sizeof(system_status_capsule_data_t);
			cap->instance = protocol_version;
			cap->magic = htons(SYSTEM_STATUS_MAGIC);
			next += sizeof(*cap);
			system_status_capsule_data_t* sscd = (system_status_capsule_data_t*)next;
			sscd->seq = peer->tx_seq++;
			sscd->rx_seq = peer->rx_seq;
			sscd->system_id = htons(system_id);
			sscd->ms = htonl(ActiveMilliseconds());
			next += sizeof(*sscd);

			// Set up operator status capsules.
			if (operator_base) {
				for (uint8_t instance = 0; instance < operator_count; instance++) {
					// Keep track of where we will write the header and data.
					capsule_header_t* h = (capsule_header_t*)next;
					operator_status_capsule_data_t* oscd = (operator_status_capsule_data_t*)(next + sizeof(capsule_header_t));
					
					// See if the operator gets filled in.
					if (operator_base->Status(instance, *oscd)) {

						// Yes; fill in the header.
						h->magic = htons(OPERATOR_STATUS_MAGIC);
						h->instance = instance;
						h->bytes_after = sizeof(operator_status_capsule_data_t);

						// Fix byte order.
						oscd->current_value = htons(oscd->current_value);
						oscd->flags = htons(oscd->flags);
						oscd->requested_value = htons(oscd->requested_value);
						oscd->time_to_achieve = htonl(oscd->time_to_achieve);

						// Move beyond the capsule header and capsule data.
						next += (sizeof(capsule_header_t) + sizeof(operator_status_capsule_data_t));
					}
				}
			}

			// Set up encoder status capsules.
			if (encoder_base) {
				for (uint8_t instance = 0; instance < encoder_count; instance++) {
					// Keep track of where we will write the header and data.
					capsule_header_t* h = (capsule_header_t*)next;
					encoder_status_capsule_data_t* escd = (encoder_status_capsule_data_t*)(next + sizeof(capsule_header_t));
					
					// See if the operator gets filled in.
					if (encoder_base->Status(instance, *escd)) {

						// Yes; fill in the header.
						h->magic = htons(ENCODER_STATUS_MAGIC);
						h->instance = instance;
						h->bytes_after = sizeof(encoder_status_capsule_data_t);

						// Fix byte order.
						escd->current_value = htons(escd->current_value);
						escd->mv = htons(escd->mv);

						// Move beyond the capsule header and capsule data.
						next += (sizeof(capsule_header_t) + sizeof(encoder_status_capsule_data_t));
					}
				}
			}

#if 0
			uint8_t *heapptr, *stackptr;
			stackptr = (uint8_t *)malloc(4);          // use stackptr temporarily
			heapptr = stackptr;                     // save value of heap pointer
  			free(stackptr);      // free up the memory again (sets stackptr to 0)
			stackptr =  (uint8_t *)(SP);           // save value of stack pointer
			DBG_ERR("HP = %p, SP = %p", heapptr, stackptr);

#endif
#if 0
			DBG_ERR("Sending %u bytes to %u.%u.%u.%u:%u", next - work,
				peer->ip[0], peer->ip[1], peer->ip[2], peer->ip[3],
				peer->port);
#endif

//			debug_packet(work, next - work);

			udp.beginPacket(peer->ip, (int)peer->port);
			udp.write(work, next - work);
			udp.endPacket();
		}
	}
#endif
}

UDPServer::~UDPServer()
{
	delete impl;
}

UDPServer::UDPServer(Preferences& prefs_, OperatorBase* operator_base_, EncoderBase* encoder_base_) :
	impl(new Impl(prefs_, operator_base_, encoder_base_))
{
}

uint32_t UDPServer::ActiveMilliseconds()
{
	return impl->ActiveMilliseconds();
}

void UDPServer::Work()
{
	impl->Work();
}

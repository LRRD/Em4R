
#include "seriallink.h"

#include <debug.h>

#include <Arduino.h>

// message := stx ticks[15..8] ticks[7..0] pos[15..8] pos[7..0] sum etx
// TICKS is how many 250us ticks should be skipped between steps, plus one.
// (e.g., 1 means to step every 250us).  If 0, the operator should not move.
// If TICKS is nonzero, POS is the desired position of the operator.
// If TICKS is zero, POS is ignored.
// SUM is the 8-bit checksum of the message payload.

// The low 8 bits of characters in which bit 8 is set.
#define	STX		0x02
#define	ETX		0x03

// USART registers (see ATmega2560 data sheet 22.10)
#pragma pack(push, 1)
struct usart_regs_t
{
	// USART Status and Control Registers (see DS 22.10.2..4)
	uint8_t reg_UCSRA;
	uint8_t reg_UCSRB;
	uint8_t reg_UCSRC;

	uint8_t dummy1;

	// USART Baud Rate Registers (see DS 22.10.5)
	uint8_t reg_UBRRL;
	uint8_t reg_UBRRH;

	uint8_t reg_UDR;
};
#pragma pack(pop)

// USART Control and Status Register A (see DS 22.10.2)
#define	UCSRA_RXC		(1U << 7)	// RX Complete (r)
#define	UCSRA_TXC		(1U << 6)	// TX Complete (r/w1tc)
#define	UCSRA_UDRE		(1U << 5)	// Data Register Empty (r)
#define	UCSRA_FE		(1U << 4)	// Frame Error (r)
#define	UCSRA_DOR		(1U << 3)	// Data Overrun (r)
#define	UCSRA_UPE		(1U << 2)	// Parity Error (r)
#define	UCSRA_U2X		(1U << 1)	// Double Speed (rw)
#define	UCSRA_MPCM		(1U << 0)	// Multiprocessor Communication Mode (rw)

// USART Control and Status Register B (see DS 22.10.3)
#define	UCSRB_RXCIE		(1U << 7)	// RX Complete Interrupt Enable (rw)
#define	UCSRB_TXCIE		(1U << 6)	// TX Complete Interrupt Enable (rw)
#define	UCSRB_UDRIE		(1U << 5)	// Data Register Empty Interrupt Enable (rw)
#define	UCSRB_RXEN		(1U << 4)	// Receiver Enable (rw)
#define	UCSRB_TXEN		(1U << 3)	// Transmitter Enable (rw)
#define	UCSRB_UCSZ2_9BITS	(1U << 2)	// 1 for 9-bit data; set UCSRC_UCSZ to 3 (rw)
#define	UCSRB_RXB8		(1U << 1)	// In 9-bit mode, RX bit 8 (r)
#define	UCSRB_TXB8		(1U << 0)	// In 9-bit mode, TX bit 8; write before UDR (rw)

// USART Control and Status Register A (see DS 22.10.4)
// UMSEL: Mode select
#define	UCSRC_UMSEL_SHIFT	6
#define	UCSRC_UMSEL_MASK	0x3U
#define	UCSRC_UMSEL_ASYNC	0U
#define	UCSRC_UMSEL_SYNC	1U
#define	UCSRC_UMSEL_RSVD_2	2U
#define	UCSRC_UMSEL_MSPIM	3U

// UPM: Parity select
#define UCSRC_UPM_SHIFT		4
#define UCSRC_UPM_MASK		0x3U
#define UCSRC_UPM_DISABLED	0U
#define UCSRC_UPM_RSVD_1	1U
#define UCSRC_UPM_EVEN		2U
#define UCSRC_UPM_ODD		3U

// USBS: Stop bits
#define	UCSRC_USBS_MASK		(1U << 3)
#define	UCSRC_USBS_1BIT		(0U << 3)
#define	UCSRC_USBS_2BITS	(1U << 3)

// UCSZ: Character size
#define	UCSRC_UCSZ_SHIFT	1
#define	UCSRC_UCSZ_MASK		0x3U
#define	UCSRC_UCSZ_5BITS	0U
#define	UCSRC_UCSZ_6BITS	1U
#define	UCSRC_UCSZ_7BITS	2U
#define	UCSRC_UCSZ_8OR9BITS	3U		// See UCSRB_UCSZ2
#define	UCSRC_UCSZ_RSVD_4	4U
#define	UCSRC_UCSZ_RSVD_5	5U
#define	UCSRC_UCSZ_RSVD_6	6U
#define	UCSRC_UCSZ_9BITS	7U

// UCPOL: Clock polarity (synchronous mode only; use 0 otherwise)
#define	UCSRC_UCPOL_MASK	(1U << 0)
#define	UCSRC_UCPOL_TXRE_RXFE	(1U << 0)
#define	UCSRC_UCPOL_TXFE_RXRE	(1U << 1)

// USART Baud Rate Registers (see DS 22.10.5)
#define	UBRRH_UBRR11_8_MASK	0xF		// Bits 11..8 of divisor

// Base addresses of the USARTs.
const uint8_t num_usarts = 4;
uint16_t usart_base[num_usarts] = { 0x00C0, 0x00C8, 0x00D0, 0x0130 };

struct SerialLink::Impl
{
	// Implement main-class methods.
	~Impl();
	Impl(uint8_t usart_, uint32_t baud_, uint16_t max_bytes_);
	bool ClearToSend();
	bool Send(const uint8_t* buf_, uint16_t bytes_);
	uint16_t Receive(uint8_t* buf_, uint16_t bytes_);
	void Work();

	// The USART ordinal (0..NUM_USARTS).

	// Registers relevant to this port.
	volatile usart_regs_t* regs;

	// The maximum message size for transmit and receive.
	uint16_t max_bytes;
	
	// The transmit and receive buffers.
	uint8_t* tx_buf;
	uint8_t* rx_buf;

	// The number of valid payload bytes in the transmit and receive buffers.
	uint16_t tx_valid_bytes;
	uint16_t rx_valid_bytes;

	// The number of payload bytes sent so far from the transmit buffer.
	uint16_t tx_sent_bytes;

	// Set once the STX has been set at the beginning of a transmission.
	bool tx_sent_stx;

	// Set if a start-of-message has been received (message being built).
	uint8_t rx_in_progress;

	// Set if a complete message payload is present in the receive buffer.
	bool rx_complete;

	// A debug buffer.
	char* debug_text;
};

SerialLink::Impl::~Impl()
{
	// Disable the USART.
	if (regs) {
		regs->reg_UCSRB &= ~(UCSRB_RXEN | UCSRB_TXEN);
	}
	delete debug_text;
}

SerialLink::Impl::Impl(uint8_t usart_, uint32_t baud_, uint16_t max_bytes_) :
	regs(nullptr),
	max_bytes(max_bytes_),
	tx_buf(nullptr),
	rx_buf(nullptr),
	tx_valid_bytes(0),
	rx_valid_bytes(0),
	tx_sent_bytes(0),
	tx_sent_stx(false),
	rx_in_progress(0),
	rx_complete(false),
	debug_text(new char[10 + (max_bytes_ * 3)])
{
	// Get a pointer to the USART registers.
	ASSERT(usart_ < num_usarts);
	regs = (volatile usart_regs_t*)usart_base[usart_];

	// Allocate the buffers.
	tx_buf = new uint8_t[max_bytes];
	rx_buf = new uint8_t[max_bytes];

	// Disable the USART.
	regs->reg_UCSRB &= ~(UCSRB_RXEN | UCSRB_TXEN);

	// Clear TX complete; single speed; no multiprocessor mode.
	regs->reg_UCSRA = (regs->reg_UCSRA & ~(UCSRA_U2X | UCSRA_MPCM)) | UCSRA_TXC;

	// Set the baud rate divisor.
	uint16_t divisor = ((uint32_t)(F_CPU) / (baud_ << 4)) - 1;
	regs->reg_UBRRH = (divisor >> 8) & UBRRH_UBRR11_8_MASK;
	regs->reg_UBRRL = divisor & 0xFF;
	DBG_ERR("SerialLink: USART%u link speed %lu baud, CPU clock %lu.%06lu MHz, divisor %u",
		usart_, baud_, (F_CPU) / 1000000UL, (F_CPU) % 1000000UL, divisor);

	// Set for async mode, no parity, 1 stop bit, 9 data bits.
	regs->reg_UCSRB |= UCSRB_UCSZ2_9BITS;
	regs->reg_UCSRC = (UCSRC_UMSEL_ASYNC << UCSRC_UMSEL_SHIFT) |
		(UCSRC_UPM_DISABLED << UCSRC_UPM_SHIFT) |
		UCSRC_USBS_1BIT |
		(UCSRC_UCSZ_8OR9BITS << UCSRC_UCSZ_SHIFT);

	// Enable transmitter and reciever.
	regs->reg_UCSRB |= (UCSRB_RXEN | UCSRB_TXEN);
}

bool SerialLink::Impl::ClearToSend()
{
	return (tx_valid_bytes == 0);
}

bool SerialLink::Impl::Send(const uint8_t* buf_, uint16_t bytes_)
{
//	DBG_ERR("send %u, max %u, tvb %u", bytes_, max_bytes, tx_valid_bytes);
	// If there is already a message in progress, we cannot send.
	if (tx_valid_bytes) {
		return false;
	}

	// Just eat empty messages.
	if (!bytes_) {
		return true;
	}

	// Copy the user data into the transmit buffer.
	ASSERT(bytes_ <= max_bytes);
	memcpy(tx_buf, buf_, bytes_);
	tx_valid_bytes = bytes_;

	// Set up to transmit.
	tx_sent_bytes = 0;

#if 0
	// Output the message for debugging.
	debug_text[0] = 0;
	char byte_text[4];
	for (uint16_t byte = 0; byte < tx_valid_bytes; byte++) {
		snprintf(byte_text, sizeof(byte_text), "%02X ", tx_buf[byte]);
		strcat(debug_text, byte_text);
	}
	DBG_ERR("TX %s", debug_text);
#endif
}

uint16_t SerialLink::Impl::Receive(uint8_t* buf_, uint16_t bytes_)
{
	// If there is no complete message, we cannot return anything.
	if (!rx_complete) {
		return 0;
	}

	// Copy the message into the supplied buffer.
	ASSERT(bytes_ <= max_bytes);
	if (bytes_ > rx_valid_bytes) {
		bytes_ = rx_valid_bytes;
	}
	memcpy(buf_, rx_buf, bytes_);

	// Set up to receive.
	rx_valid_bytes = 0;
	rx_complete = false;

#if 0
	// Output the message for debugging.
	debug_text[0] = 0;
	char byte_text[4];
	for (uint16_t byte = 0; byte < bytes_; byte++) {
		snprintf(byte_text, sizeof(byte_text), "%02X ", buf_[byte]);
		strcat(debug_text, byte_text);
	}
	DBG_ERR("RX %s", debug_text);
#endif
	
	// Return the number of bytes we copied.
	return bytes_;
}

void SerialLink::Impl::Work()
{
	uint8_t ucsra = regs->reg_UCSRA;

	// Check for a received character.
	if (ucsra & UCSRA_RXC) {

		// Read the character.
		uint8_t bit8 = (regs->reg_UCSRB & UCSRB_RXB8) ? 1U : 0;
		uint8_t low8 = regs->reg_UDR;

		// Control character?
		if (bit8) {
			//DBG_ERR("%02X RX *%02X", ucsra, low8);
			switch (low8) {
			case STX:
				// Start a new message.
				rx_complete = false;
				rx_valid_bytes = 0;
				rx_in_progress = 1;
				break;

			case ETX:
				// Complete a message in progress.
				if (rx_in_progress) {
					rx_complete = true;
				}
				break;
			
			default:
				// Unknown control character.
				break;
			}
		}

		// Regular character?
		else if (rx_in_progress) {
			//DBG_ERR("%02X RX  %02X", ucsra, low8);
			if (rx_valid_bytes < max_bytes) {
				rx_buf[rx_valid_bytes++] = low8;
			}
		}
	}

	// Check for the ability to transmit.
	if (tx_valid_bytes && (ucsra & UCSRA_UDRE)) {

		// If this is the first character, sent the STX.
		if ((tx_sent_bytes == 0) && !tx_sent_stx) {
			regs->reg_UCSRB |= UCSRB_TXB8;
			regs->reg_UDR = STX;
			tx_sent_stx = true;
			//DBG_ERR("   TX *%02X", STX);
		}

		// If we already sent the last character, send the ETX.
		else if (tx_sent_bytes == tx_valid_bytes) {
			regs->reg_UCSRB |= UCSRB_TXB8;
			regs->reg_UDR = ETX;
			//DBG_ERR("   TX *%02X", ETX);

			// Nothing else to send.
			tx_sent_bytes = tx_valid_bytes = 0;
			tx_sent_stx = false;
		}

		// Send the next character.
		else {
			regs->reg_UCSRB &= ~UCSRB_TXB8;
			//DBG_ERR("   TX  %02X", tx_buf[tx_sent_bytes]);
			regs->reg_UDR = tx_buf[tx_sent_bytes++];
		}
	}
}

SerialLink::~SerialLink()
{
	delete impl;
}

SerialLink::SerialLink(uint8_t usart_, uint32_t baud_, uint16_t max_bytes_) :
	impl(new Impl(usart_, baud_, max_bytes_))
{
}

bool SerialLink::ClearToSend()
{
	return impl->ClearToSend();
}

bool SerialLink::Send(const uint8_t* buf_, uint16_t bytes_)
{
	return impl->Send(buf_, bytes_);
}

uint16_t SerialLink::Receive(uint8_t* buf_, uint16_t bytes_)
{
	return impl->Receive(buf_, bytes_);
}

void SerialLink::Work()
{
	impl->Work();
}

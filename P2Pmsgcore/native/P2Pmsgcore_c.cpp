// P2Pmsgcore_c.cpp  –  extern "C" implementation of Panama bridge
// Compiles as part of P2Pmsgcore.dll (P2Pmsgcore_EXPORTS defined via project
// preprocessor settings; do not redefine here).
// stdafx.h MUST be the first include to satisfy the precompiled-header requirement.

#include "stdafx.h"          // precompiled header – must be first
#include "P2Pmsgcore_c.h"
#include "P2Peer.h"
#include "P2PeerMsg.h"
#include "P2PeerCon.h"
#include "P2PeerConWsa.h"
#include "P2PeerHub.h"
#include "P2Pwin32.h"        // StartupP2Pmsg / CleanupP2Pmsg (P2Pmsg environment)

// ─────────────────────────────────────────────────────────────────────────────
// Cast helpers
// ─────────────────────────────────────────────────────────────────────────────
static inline P2Paddr*      addr(P2PAddrHandle      h) { return static_cast<P2Paddr*>(h); }
static inline P2PeerMsg*    msg (P2PeerMsgHandle     h) { return static_cast<P2PeerMsg*>(h); }
static inline P2PeerConWsa* wsa (P2PeerConWsaHandle  h) { return static_cast<P2PeerConWsa*>(h); }
static inline P2PeerHub*    hub (P2PeerHubHandle     h) { return static_cast<P2PeerHub*>(h); }

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle (P2Pmsg environment)
// StartupP2Pmsg initialises the shared hub/pump critical sections and the hub
// manager table; it MUST run once before any P2PeerHub operation, otherwise
// CreateP2PmsgHub enters uninitialised critical sections and crashes. Every
// native consumer calls it; the C/Panama surface needs its own entry point.
// Wrapped in try/catch because the underlying code signals errors by throwing
// P2Pevent, and a C++ exception must never unwind across the extern "C" ABI.
// ─────────────────────────────────────────────────────────────────────────────

int p2pmsgcore_startup(unsigned int nMaxHubs)
{
    try            { return StartupP2Pmsg(nMaxHubs) ? 1 : 0; }
    catch (...)    { return 0; }
}

void p2pmsgcore_cleanup(void)
{
    try            { CleanupP2Pmsg(); }
    catch (...)    { /* best-effort teardown */ }
}

// ─────────────────────────────────────────────────────────────────────────────
// P2Paddr
// ─────────────────────────────────────────────────────────────────────────────

P2PAddrHandle p2paddr_create()
{
    return new P2Paddr();
}

P2PAddrHandle p2paddr_create_str(const wchar_t* strAddr)
{
    return new P2Paddr(strAddr);
}

void p2paddr_destroy(P2PAddrHandle h)
{
    delete addr(h);
}

const wchar_t* p2paddr_c_name(P2PAddrHandle h)
{
    return addr(h)->c_name();
}

int p2paddr_is_null(P2PAddrHandle h)
{
    return addr(h)->IsNull() ? 1 : 0;
}

int p2paddr_is_empty(P2PAddrHandle h)
{
    return addr(h)->IsEmpty() ? 1 : 0;
}

unsigned short p2paddr_sizeof(P2PAddrHandle h)
{
    return addr(h)->Sizeof();
}

int p2paddr_is_child(P2PAddrHandle h, const wchar_t* strAddr)
{
    return addr(h)->IsChild(strAddr) ? 1 : 0;
}

int p2paddr_is_rable(P2PAddrHandle h, const wchar_t* strAddr)
{
    return addr(h)->IsRable(strAddr) ? 1 : 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// P2PeerMsg
// ─────────────────────────────────────────────────────────────────────────────

P2PeerMsgHandle p2peermsg_create()
{
    return new P2PeerMsg();
}

P2PeerMsgHandle p2peermsg_create_full(const wchar_t* src,
                                       const wchar_t* dst,
                                       const wchar_t* msgID,
                                       const void*    data,
                                       unsigned short dataSize)
{
    return new P2PeerMsg(src, dst, msgID, data, static_cast<P2Psize_t>(dataSize));
}

P2PeerMsgHandle p2peermsg_create_msgid(const wchar_t* msgID)
{
    return new P2PeerMsg(msgID);
}

void p2peermsg_destroy(P2PeerMsgHandle h)
{
    delete msg(h);
}

const wchar_t* p2peermsg_get_source(P2PeerMsgHandle h)
{
    return msg(h)->GetSource();
}

void p2peermsg_set_source(P2PeerMsgHandle h, const wchar_t* src)
{
    msg(h)->SetSource(src);
}

const wchar_t* p2peermsg_get_destin(P2PeerMsgHandle h)
{
    return msg(h)->GetDestin();
}

void p2peermsg_set_destin(P2PeerMsgHandle h, const wchar_t* dst)
{
    msg(h)->SetDestin(dst);
}

const wchar_t* p2peermsg_c_name(P2PeerMsgHandle h)
{
    return msg(h)->c_name();
}

const char* p2peermsg_data(P2PeerMsgHandle h)
{
    return msg(h)->Data();
}

int64_t p2peermsg_data_size(P2PeerMsgHandle h)
{
    return static_cast<int64_t>(msg(h)->DataSize());
}

unsigned char p2peermsg_priority(P2PeerMsgHandle h)
{
    return msg(h)->Priority();
}

unsigned char p2peermsg_set_priority(P2PeerMsgHandle h, unsigned char pri)
{
    return msg(h)->SetPriority(pri);
}

unsigned short p2peermsg_sizeof(P2PeerMsgHandle h)
{
    return msg(h)->Sizeof();
}

int p2peermsg_is_wrapped(P2PeerMsgHandle h)
{
    return msg(h)->IsWrapped() ? 1 : 0;
}

int p2peermsg_is_reflected(P2PeerMsgHandle h)
{
    return msg(h)->IsReflected() ? 1 : 0;
}

P2PeerMsgHandle p2peermsg_response_factory(P2PeerMsgHandle h,
                                            const wchar_t*  msgID,
                                            const void*     data,
                                            unsigned short  dataSize)
{
    return msg(h)->ResponseFactory(msgID, data, static_cast<P2Psize_t>(dataSize));
}

P2PeerMsgHandle p2peermsg_redirect_factory(P2PeerMsgHandle h, const wchar_t* dstAddr)
{
    return msg(h)->RedirectFactory(dstAddr);
}

// ─────────────────────────────────────────────────────────────────────────────
// P2PeerConWsa
// ─────────────────────────────────────────────────────────────────────────────

P2PeerConWsaHandle p2peerconwsa_client_factory(const wchar_t* strThatAddr,
                                                const wchar_t* ipAddress,
                                                short          port)
{
    return P2PeerConWsa::ClientFactory(strThatAddr, ipAddress, port);
}

P2PeerConWsaHandle p2peerconwsa_service_factory(const wchar_t* strThatAddr,
                                                 short          port)
{
    return P2PeerConWsa::ServiceFactory(strThatAddr, port);
}

void p2peerconwsa_destroy(P2PeerConWsaHandle h)
{
    delete wsa(h);
}

int p2peerconwsa_connect(P2PeerConWsaHandle h)
{
    return wsa(h)->Connect() ? 1 : 0;
}

int p2peerconwsa_listen(P2PeerConWsaHandle h)
{
    return wsa(h)->Listen() ? 1 : 0;
}

void p2peerconwsa_close(P2PeerConWsaHandle h)
{
    wsa(h)->Close();
}

unsigned long p2peerconwsa_get_state(P2PeerConWsaHandle h, unsigned long mask)
{
    return wsa(h)->GetState(static_cast<DWORD>(mask));
}

int p2peerconwsa_has_state(P2PeerConWsaHandle h, unsigned long mask)
{
    return wsa(h)->HasState(static_cast<DWORD>(mask)) ? 1 : 0;
}

int p2peerconwsa_get_mode(P2PeerConWsaHandle h)
{
    return static_cast<int>(wsa(h)->GetMode());
}

const wchar_t* p2peerconwsa_get_address(P2PeerConWsaHandle h)
{
    return wsa(h)->GetP2Paddress().c_name();
}

P2PeerMsgHandle p2peerconwsa_post_msg(P2PeerConWsaHandle h, P2PeerMsgHandle m)
{
    return wsa(h)->PostP2PeerMsg(msg(m));
}

// ─────────────────────────────────────────────────────────────────────────────
// P2PeerHub
// ─────────────────────────────────────────────────────────────────────────────

P2PeerHubHandle p2peerhub_create(const wchar_t* strAddr)
{
    return new P2PeerHub(strAddr);
}

void p2peerhub_destroy(P2PeerHubHandle h)
{
    delete hub(h);
}

int p2peerhub_create_hub(P2PeerHubHandle h,
                          const wchar_t*  strAddr,
                          unsigned int    pumpsMax)
{
    return hub(h)->CreateHub(strAddr, pumpsMax) ? 1 : 0;
}

void* p2peerhub_spawn_hub(P2PeerHubHandle h)
{
    return hub(h)->SpawnHub();
}

void p2peerhub_close_hub(P2PeerHubHandle h)
{
    hub(h)->CloseHub();
}

void p2peerhub_pause_hub(P2PeerHubHandle h)
{
    hub(h)->PauseHub();
}

void p2peerhub_wakeup_hub(P2PeerHubHandle h)
{
    hub(h)->WakeupHub();
}

int p2peerhub_post_con(P2PeerHubHandle    h,
                        P2PeerConWsaHandle con,
                        unsigned int       pumpID)
{
    return hub(h)->PostP2PeerCon(wsa(con), static_cast<P2PumpID>(pumpID)) ? 1 : 0;
}

int p2peerhub_con_exists(P2PeerHubHandle h, const wchar_t* addr_str)
{
    return hub(h)->ConExists(addr_str) ? 1 : 0;
}

P2PeerMsgHandle p2peerhub_post_msg(P2PeerHubHandle h, P2PeerMsgHandle m)
{
    return hub(h)->PostP2PeerMsg(msg(m));
}

unsigned long p2peerhub_get_hub_id(P2PeerHubHandle h)
{
    return static_cast<unsigned long>(hub(h)->GetHubID());
}

const wchar_t* p2peerhub_get_address(P2PeerHubHandle h)
{
    return hub(h)->GetP2PaddrHub().c_name();
}

// P2Pmsgcore_c.h  –  extern "C" wrapper for Java Panama FFI
// Exposes: P2Paddr, P2PeerMsg, P2PeerConWsa, P2PeerHub
// String convention: all TCHAR/wchar_t strings are wchar_t* (Windows UNICODE build).
// Ownership: strings returned by getters point to internal C++ object storage;
//            they are valid only while the owning handle is alive.

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

// Export/import decoration. Kept self-contained (no dependency on the Platform
// __declspec shim) so any consumer -- the TreeFS/FUSE frontend, Java Panama /
// jextract, etc. -- can include this header directly on either toolchain. Mirrors
// Msgcore_c.h's MSGCORE_C_API. Windows behaviour is byte-identical to before.
#if defined(_WIN32)
#  if defined(P2Pmsgcore_EXPORTS)
#    define P2PC_API __declspec(dllexport)
#  elif defined(P2Pmsgcore_STATIC)
#    define P2PC_API
#  else
#    define P2PC_API __declspec(dllimport)
#  endif
#else                                   // GCC/Clang (Linux port)
#  if defined(P2Pmsgcore_EXPORTS)
#    define P2PC_API __attribute__((visibility("default")))
#  else
#    define P2PC_API               // imports need no decoration on ELF
#  endif
#endif

#include <stdint.h>
#include <wchar.h>

// ── Opaque handles ────────────────────────────────────────────────────────────
typedef void* P2PAddrHandle;
typedef void* P2PeerMsgHandle;
typedef void* P2PeerConWsaHandle;
typedef void* P2PeerHubHandle;

// ── Lifecycle (P2Pmsg environment) ────────────────────────────────────────────
// p2pmsgcore_startup MUST be called once per process BEFORE any P2PeerHub
// operation (it initialises the shared hub/pump critical sections and the hub
// manager table); p2pmsgcore_cleanup once at shutdown. This mirrors the native
// StartupP2Pmsg/CleanupP2Pmsg contract every C++ consumer already follows.
// P2Paddr and P2PeerMsg (pure object model) do NOT require startup.
// Returns 1 on success, 0 on failure (incl. "already started").
P2PC_API int            p2pmsgcore_startup    (unsigned int nMaxHubs);
P2PC_API void           p2pmsgcore_cleanup    (void);

// ── P2Paddr ───────────────────────────────────────────────────────────────────
P2PC_API P2PAddrHandle  p2paddr_create        (void);
P2PC_API P2PAddrHandle  p2paddr_create_str    (const wchar_t* strAddr);
P2PC_API void           p2paddr_destroy       (P2PAddrHandle h);
P2PC_API const wchar_t* p2paddr_c_name        (P2PAddrHandle h);
P2PC_API int            p2paddr_is_null       (P2PAddrHandle h);
P2PC_API int            p2paddr_is_empty      (P2PAddrHandle h);
P2PC_API unsigned short p2paddr_sizeof        (P2PAddrHandle h);
P2PC_API int            p2paddr_is_child      (P2PAddrHandle h, const wchar_t* strAddr);
P2PC_API int            p2paddr_is_rable      (P2PAddrHandle h, const wchar_t* strAddr);

// ── P2PeerMsg ─────────────────────────────────────────────────────────────────
P2PC_API P2PeerMsgHandle  p2peermsg_create         (void);
P2PC_API P2PeerMsgHandle  p2peermsg_create_full    (const wchar_t* src,
                                                     const wchar_t* dst,
                                                     const wchar_t* msgID,
                                                     const void*    data,
                                                     unsigned short dataSize);
P2PC_API P2PeerMsgHandle  p2peermsg_create_msgid   (const wchar_t* msgID);
P2PC_API void             p2peermsg_destroy        (P2PeerMsgHandle h);

P2PC_API const wchar_t*   p2peermsg_get_source     (P2PeerMsgHandle h);
P2PC_API void             p2peermsg_set_source     (P2PeerMsgHandle h, const wchar_t* src);
P2PC_API const wchar_t*   p2peermsg_get_destin     (P2PeerMsgHandle h);
P2PC_API void             p2peermsg_set_destin     (P2PeerMsgHandle h, const wchar_t* dst);
P2PC_API const wchar_t*   p2peermsg_c_name         (P2PeerMsgHandle h);
P2PC_API const char*      p2peermsg_data           (P2PeerMsgHandle h);
P2PC_API int64_t          p2peermsg_data_size      (P2PeerMsgHandle h);
P2PC_API unsigned char    p2peermsg_priority       (P2PeerMsgHandle h);
P2PC_API unsigned char    p2peermsg_set_priority   (P2PeerMsgHandle h, unsigned char pri);
P2PC_API unsigned short   p2peermsg_sizeof         (P2PeerMsgHandle h);
P2PC_API int              p2peermsg_is_wrapped     (P2PeerMsgHandle h);
P2PC_API int              p2peermsg_is_reflected   (P2PeerMsgHandle h);
// Returns a newly allocated P2PeerMsg that the caller is responsible for destroying.
P2PC_API P2PeerMsgHandle  p2peermsg_response_factory    (P2PeerMsgHandle h,
                                                          const wchar_t* msgID,
                                                          const void* data,
                                                          unsigned short dataSize);
P2PC_API P2PeerMsgHandle  p2peermsg_redirect_factory    (P2PeerMsgHandle h,
                                                          const wchar_t* dstAddr);

// ── P2PeerConWsa ──────────────────────────────────────────────────────────────
// Client connection: active outbound TCP connect
P2PC_API P2PeerConWsaHandle p2peerconwsa_client_factory   (const wchar_t* strThatAddr,
                                                            const wchar_t* ipAddress,
                                                            short          port);
// Service connection: passive inbound TCP listen
P2PC_API P2PeerConWsaHandle p2peerconwsa_service_factory  (const wchar_t* strThatAddr,
                                                            short          port);
P2PC_API void               p2peerconwsa_destroy          (P2PeerConWsaHandle h);
P2PC_API int                p2peerconwsa_connect          (P2PeerConWsaHandle h);
P2PC_API int                p2peerconwsa_listen           (P2PeerConWsaHandle h);
P2PC_API void               p2peerconwsa_close            (P2PeerConWsaHandle h);
P2PC_API unsigned long      p2peerconwsa_get_state        (P2PeerConWsaHandle h, unsigned long mask);
P2PC_API int                p2peerconwsa_has_state        (P2PeerConWsaHandle h, unsigned long mask);
// Returns P2PeerConMode_e: 0=Unknown,1=CLIENT,2=SERVICE,3=Accept
P2PC_API int                p2peerconwsa_get_mode         (P2PeerConWsaHandle h);
P2PC_API const wchar_t*     p2peerconwsa_get_address      (P2PeerConWsaHandle h);
// PostP2PeerMsg takes ownership of msgHandle; do not destroy it after this call.
P2PC_API P2PeerMsgHandle    p2peerconwsa_post_msg         (P2PeerConWsaHandle h,
                                                            P2PeerMsgHandle    msg);

// ── P2PeerHub ─────────────────────────────────────────────────────────────────
P2PC_API P2PeerHubHandle    p2peerhub_create         (const wchar_t* strAddr);
P2PC_API void               p2peerhub_destroy        (P2PeerHubHandle h);
P2PC_API int                p2peerhub_create_hub     (P2PeerHubHandle h,
                                                       const wchar_t* strAddr,
                                                       unsigned int   pumpsMax);
// Spawns the hub thread; returns a Win32 HANDLE (cast to void*).
P2PC_API void*              p2peerhub_spawn_hub      (P2PeerHubHandle h);
P2PC_API void               p2peerhub_close_hub      (P2PeerHubHandle h);
P2PC_API void               p2peerhub_pause_hub      (P2PeerHubHandle h);
P2PC_API void               p2peerhub_wakeup_hub     (P2PeerHubHandle h);
// PostP2PeerCon passes ownership of the connection to the hub.
P2PC_API int                p2peerhub_post_con       (P2PeerHubHandle    hub,
                                                       P2PeerConWsaHandle con,
                                                       unsigned int       pumpID);
P2PC_API int                p2peerhub_con_exists     (P2PeerHubHandle h, const wchar_t* addr);
// PostP2PeerMsg takes ownership; do not destroy msg after this call.
P2PC_API P2PeerMsgHandle    p2peerhub_post_msg       (P2PeerHubHandle h, P2PeerMsgHandle msg);
P2PC_API unsigned long      p2peerhub_get_hub_id     (P2PeerHubHandle h);
P2PC_API const wchar_t*     p2peerhub_get_address    (P2PeerHubHandle h);

// ── UTF-8 (_u8) parallel entry points ─────────────────────────────────────────
// The recommended cross-platform FFI surface (LinuxPortPlan.md §4.2, §6.2). The
// wchar_t entry points above use each platform's native wide layout (UTF-16 on
// Windows, UTF-32 on Linux) and cannot carry a string portably through Panama.
// These _u8 twins take/return UTF-8 (const char*), converting at the boundary,
// and are ABI-identical on both OSes. Only string-bearing functions get a twin.
// Returned const char* points at a thread-local buffer valid until the next _u8
// string-returning call on the same thread (mirrors the wchar_t getters). Note
// p2peermsg_data() is an opaque binary payload, not text — it has no _u8 twin.

// P2Paddr
P2PC_API P2PAddrHandle  p2paddr_create_str_u8 (const char* strAddr);
P2PC_API const char*    p2paddr_c_name_u8     (P2PAddrHandle h);
P2PC_API int            p2paddr_is_child_u8   (P2PAddrHandle h, const char* strAddr);
P2PC_API int            p2paddr_is_rable_u8   (P2PAddrHandle h, const char* strAddr);

// P2PeerMsg
P2PC_API P2PeerMsgHandle p2peermsg_create_full_u8 (const char* src, const char* dst,
                                                   const char* msgID, const void* data,
                                                   unsigned short dataSize);
P2PC_API P2PeerMsgHandle p2peermsg_create_msgid_u8(const char* msgID);
P2PC_API const char*     p2peermsg_get_source_u8  (P2PeerMsgHandle h);
P2PC_API void            p2peermsg_set_source_u8  (P2PeerMsgHandle h, const char* src);
P2PC_API const char*     p2peermsg_get_destin_u8  (P2PeerMsgHandle h);
P2PC_API void            p2peermsg_set_destin_u8  (P2PeerMsgHandle h, const char* dst);
P2PC_API const char*     p2peermsg_c_name_u8      (P2PeerMsgHandle h);
P2PC_API P2PeerMsgHandle p2peermsg_response_factory_u8(P2PeerMsgHandle h, const char* msgID,
                                                       const void* data, unsigned short dataSize);
P2PC_API P2PeerMsgHandle p2peermsg_redirect_factory_u8(P2PeerMsgHandle h, const char* dstAddr);

// P2PeerConWsa
P2PC_API P2PeerConWsaHandle p2peerconwsa_client_factory_u8 (const char* strThatAddr,
                                                            const char* ipAddress, short port);
P2PC_API P2PeerConWsaHandle p2peerconwsa_service_factory_u8(const char* strThatAddr, short port);
P2PC_API const char*        p2peerconwsa_get_address_u8    (P2PeerConWsaHandle h);

// P2PeerHub
P2PC_API P2PeerHubHandle p2peerhub_create_u8     (const char* strAddr);
P2PC_API int             p2peerhub_create_hub_u8 (P2PeerHubHandle h, const char* strAddr, unsigned int pumpsMax);
P2PC_API int             p2peerhub_con_exists_u8 (P2PeerHubHandle h, const char* addr);
P2PC_API const char*     p2peerhub_get_address_u8(P2PeerHubHandle h);

#ifdef __cplusplus
}  // extern "C"
#endif

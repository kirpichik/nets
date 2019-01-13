
#include <fcntl.h>
#include <sys/socket.h>
#include <unistd.h>
#include <iostream>

#include "socks-handler.h"

#include "proxy-state.h"

namespace proxy {

proxy_state::proxy_state(int server_socket) noexcept
    : server_socket(server_socket) {
  ares_library_init(ARES_LIB_INIT_ALL);
  ares_init(&channel);
}

void proxy_state::build_fds(fd_set* read,
                            fd_set* write,
                            fd_set* except) noexcept {
  FD_ZERO(read);
  FD_ZERO(write);
  FD_ZERO(except);

  ares_fds(channel, read, write);

  for (const auto& pair : handlers) {
    if (pair.second->is_read_required())
      FD_SET(pair.first, read);
    if (pair.second->is_write_required())
      FD_SET(pair.first, write);
    FD_SET(pair.first, except);
  }
}

int proxy_state::run() {
  int result;
  fd_set readfds;
  fd_set writefds;
  fd_set exceptfds;
  build_fds(&readfds, &writefds, &exceptfds);

  while ((result = select(server_socket, &readfds, &writefds, &exceptfds,
                          NULL)) > 0) {
    for (auto iter = handlers.begin(); iter != handlers.end(); iter++) {
      auto& pair = *iter;
      int sock = pair.first;
      bool left = false;

      if (FD_ISSET(sock, &readfds))
        left = pair.second->handle_receive();
      if (left && FD_ISSET(sock, &writefds))
        left = pair.second->handle_send();
      if (left && FD_ISSET(sock, &exceptfds)) {
        left = false;
        std::cerr << "Exception in " << sock << " socket select" << std::endl;
      }

      if (!left)
        handlers.erase(iter);
    }

    ares_process(channel, &readfds, &writefds);

    build_fds(&readfds, &writefds, &exceptfds);
  }

  if (result == -1)
    std::perror("Cannot use select");
  return result;
}

void proxy_state::register_handler(
    int socket,
    std::unique_ptr<proxy_handler> handler) noexcept {
  handlers[socket] = std::move(handler);
}

void proxy_state::unregister_handler(int socket) noexcept {
  auto it = handlers.find(socket);
  if (it != handlers.end())
    handlers.erase(it);
}

void proxy_state::resolve(const std::string& hostname,
                          resolve_callback callback,
                          void* arg) noexcept {
  ares_gethostbyname(channel, hostname.c_str(), AF_INET, callback, arg);
}

proxy_state::~proxy_state() noexcept {
  ares_destroy(channel);
  ares_library_cleanup();
}

bool server_handler::handle_receive() noexcept {
  int sock;
  if ((sock = accept(fd, NULL, NULL)) < 0) {
    std::perror("Cannot accept new client");
    return false;
  }

  if (fcntl(sock, F_SETFL, O_NONBLOCK) < 0) {
    std::perror("Cannot set non-block for client socket");
    close(sock);
    return false;
  }

  state->register_handler(sock,
                          std::make_unique<socks::socks_handler>(state, sock));
  return true;
}

bool server_handler::handle_send() noexcept {
  std::cerr << "Unsupported action on server handler" << std::endl;
  return true;
}

}  // namespace proxy

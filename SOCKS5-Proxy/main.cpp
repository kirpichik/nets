
#include <fcntl.h>
#include <netdb.h>
#include <signal.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>
#include <iostream>
#include <memory>

#include "proxy-state.h"

int main(int argc, char* argv[]) {
  if (argc < 2) {
    std::cerr << "Usage: <listen_port>" << std::endl;
    return -1;
  }

  int server_socket;
  if ((server_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    std::perror("Cannot create server socket");
    return -1;
  }

  sockaddr_in addr = {.sin_family = AF_INET,
                      .sin_port = htons(std::atoi(argv[1])),
                      .sin_addr.s_addr = htonl(INADDR_ANY)};

  if (bind(server_socket, reinterpret_cast<sockaddr*>(&addr), sizeof(addr))) {
    std::perror("Cannot bind server socket");
    close(server_socket);
    return -1;
  }

  if (fcntl(server_socket, F_SETFL, O_NONBLOCK) < 0) {
    std::perror("Cannot set non-block to server socket");
    close(server_socket);
    return -1;
  }

  if (listen(server_socket, FD_SETSIZE)) {
    std::perror("Cannot listen server socket");
    close(server_socket);
    return -1;
  }

  std::shared_ptr<proxy::proxy_state> state =
      std::make_shared<proxy::proxy_state>(server_socket);
  state->register_handler(
      server_socket,
      std::make_unique<proxy::server_handler>(state, server_socket));
  return state->run();
}

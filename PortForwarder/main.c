
#include <stdbool.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <poll.h>
#include <netdb.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <sys/socket.h>

#define BUFFER_SIZE 4096
#define POLLS_INIT_SIZE 50
#define POLLS_INCREASE_SPEED 2

typedef struct forward {
  int socket;
  size_t pos;
  size_t size;
  bool out_down;
  bool in_down;
  struct forward* output;
  char buffer[BUFFER_SIZE];
} forward_t;

typedef struct proxy_state {
  size_t polls_count;
  size_t polls_size;
  struct addrinfo* target;
  forward_t* forwards;
  struct pollfd* polls;
} proxy_state_t;

static proxy_state_t state;

static void remove_socket_at(size_t pos) {
  state.polls_count--;
  close(state.polls[pos].fd);
  memcpy(&state.polls[pos], &state.polls[state.polls_count], sizeof(struct pollfd));
  memcpy(&state.forwards[pos], &state.forwards[state.polls_count], sizeof(forward_t));
  memset(&state.polls[state.polls_count], 0, sizeof(struct pollfd));
  state.forwards[pos].pos = pos;
  state.forwards[pos].output->output = state.forwards + pos;
}

static void remove_forward(forward_t* forward) {
  size_t pos = forward->pos;
  size_t other = forward->output->pos;
  remove_socket_at(forward->pos);
  if (other == state.polls_count)
    remove_socket_at(pos);
  else
    remove_socket_at(other);
}

static bool handle_socket(size_t pos) {
  int events = state.polls[pos].revents;
  int socket = state.polls[pos].fd;
  forward_t* forward = &state.forwards[pos];
  ssize_t len;

  if (events & (POLLIN | POLLPRI)) {
    len = recv(socket, forward->buffer + forward->size, BUFFER_SIZE - forward->size, 0);
    if (len < 0) {
      perror("Cannot receive data");
      remove_forward(forward);
      return false;
    } else if (len == 0) {
      shutdown(socket, SHUT_RD);
      state.polls[pos].events &= ~POLLIN;
      forward->in_down = forward->output->out_down = true;
    }

    forward->size += len;
    state.polls[forward->output->pos].events |= POLLOUT;
    if (forward->size == BUFFER_SIZE)
      state.polls[pos].events &= ~POLLIN;
  }

  if (events & POLLOUT) {
    len = send(socket, forward->output->buffer, forward->output->size, 0);
    if (len < 0) {
      perror("Cannot send data");
      remove_forward(forward);
      return false;
    }

    forward->output->size -= len;
    memmove(forward->output->buffer, forward->output->buffer + len, forward->output->size);
    if (!forward->output->in_down)
      state.polls[forward->output->pos].events |= POLLIN;
    if (forward->output->size == 0) {
      state.polls[pos].events &= ~POLLOUT;
      if (forward->out_down)
        shutdown(socket, SHUT_WR);
    }
  }

  if (forward->out_down && forward->in_down && forward->size == 0 && forward->output->size == 0) {
    remove_forward(forward);
    return false;
  }

  return true;
}

static bool register_forward(int client_socket, int target_socket) {
  struct pollfd* temp_polls;
  forward_t* temp_forwards;
  forward_t* client;
  forward_t* target;

  if (state.polls_count + 2 >= state.polls_size) {
    state.polls_size *= POLLS_INCREASE_SPEED;

    temp_polls = (struct pollfd*)realloc(state.polls, state.polls_size * sizeof(struct pollfd));
    if (temp_polls == NULL)
      return false;
    state.polls = temp_polls;

    temp_forwards = (forward_t*)realloc(state.forwards, state.polls_size * sizeof(forward_t));
    if (temp_forwards == NULL)
      return false;
    state.forwards = temp_forwards;
  }

  memset(&state.polls[state.polls_count], 0, sizeof(struct pollfd));
  state.polls[state.polls_count].fd = client_socket;
  state.polls[state.polls_count].events = POLLIN;
  client = state.forwards + state.polls_count;
  memset(client, 0, sizeof(forward_t));
  client->socket = client_socket;
  client->pos = state.polls_count;

  state.polls_count++;

  memset(&state.polls[state.polls_count], 0, sizeof(struct pollfd));
  state.polls[state.polls_count].fd = target_socket;
  state.polls[state.polls_count].events = POLLIN;
  target = state.forwards + state.polls_count;
  memset(target, 0, sizeof(forward_t));
  target->socket = target_socket;
  target->pos = state.polls_count;

  state.polls_count++;

  client->output = target;
  target->output = client;

  return true;
}

static bool handle_server_socket(void) {
  int client_socket;
  int target_socket;

  if (!(state.polls[0].revents & (POLLIN | POLLPRI))) {
    fprintf(stderr, "Cannot accept new clients\n");
    return false;
  }

  if ((client_socket = accept(state.polls[0].fd, NULL, NULL)) < 0) {
    perror("Cannot accept new client");
    return false;
  }
  
  if (fcntl(client_socket, F_SETFL, O_NONBLOCK) < 0) {
    perror("Cannot set non-block for socket");
    close(client_socket);
    return false;
  }
  
  target_socket = socket(state.target->ai_family, state.target->ai_socktype, state.target->ai_protocol);
  if (target_socket < 0) {
    perror("Cannot create target socket");
    close(client_socket);
    return false;
  }
  
  if (connect(target_socket, state.target->ai_addr, state.target->ai_addrlen) < 0) {
    perror("Cannot connect to target");
    close(target_socket);
    close(client_socket);
    return false;
  }
  
  if (!register_forward(client_socket, target_socket)) {
    close(client_socket);
    close(target_socket);
    return false;
  }

  return true;
}

static void proxy_executor(void) {
  int count;

  while (1) {
    count = poll(state.polls, (nfds_t) state.polls_count, -1);
    if (count == -1) {
      if (errno == EINTR)
        continue;
      break;
    }
    
    for (size_t i = 0; i < state.polls_count; i++) {
      if (state.polls[i].revents == 0)
        continue;
      count--;
      
      if (i == 0) {
        if (!handle_server_socket())
          return;
      }
      else
        if (!handle_socket(i))
          i--;
    }
  }

  perror("Cannot poll sockets");
}

static void destroy_proxy_state() {
  fprintf(stderr, "Cleanup...\n");

  for (size_t i = 0; i < state.polls_count; i++)
    close(state.polls[0].fd);
  free(state.polls);
  free(state.forwards);
  freeaddrinfo(state.target);
}

static bool init_proxy_state(int server_socket, struct addrinfo* target) {
  state.polls = (struct pollfd*)calloc(POLLS_INIT_SIZE, sizeof(struct pollfd));
  if (!state.polls) {
    perror("Cannot allocate polls memory");
    return false;
  }

  state.forwards = (forward_t*)malloc(POLLS_INIT_SIZE * sizeof(forward_t));
  if (!state.forwards) {
    perror("Cannot allocate forwards memory");
    free(state.polls);
    return false;
  }

  state.polls[0].fd = server_socket;
  state.polls[0].events = POLLIN;
  state.polls_size = POLLS_INIT_SIZE;
  state.polls_count = 1;
  state.target = target;
  return true;
}

static void interrupt_signal(int signal) {
  destroy_proxy_state();
  exit(0);
}

int main(int argc, char* argv[]) {
  struct addrinfo hints, *result;
  struct sockaddr_in addr;
  int server_socket;
  int server_port;
  int error;
  
  if (argc != 4) {
    fprintf(stderr, "Need args: <listen_port> <target_hostname> <target_port>\n");
    return -1;
  }
  
  memset(&hints, 0, sizeof(struct addrinfo));
  hints.ai_family = PF_UNSPEC;
  hints.ai_socktype = SOCK_STREAM;
  if ((error = getaddrinfo(argv[2], argv[3], &hints, &result)) != 0) {
    fprintf(stderr, "Cannot resolve %s: %s\n", argv[2], gai_strerror(error));
    return -1;
  }

  server_port = atoi(argv[1]);
  if ((server_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    perror("Cannot create server socket");
    return -1;
  }
  
  addr.sin_family = AF_INET;
  addr.sin_port = htons(server_port);
  addr.sin_addr.s_addr = htonl(INADDR_ANY);
  
  if (bind(server_socket, (struct sockaddr*)&addr, sizeof(addr))) {
    perror("Cannot bind server socket");
    return -1;
  }

  if (fcntl(server_socket, F_SETFL, O_NONBLOCK) < 0) {
    perror("Cannot set non-block to server socket");
    close(server_socket);
    freeaddrinfo(result);
    return -1;
  }

  if (listen(server_socket, POLLS_INIT_SIZE) < 0) {
    perror("Cannot listen server socket");
    close(server_socket);
    freeaddrinfo(result);
    return -1;
  }

  if (!init_proxy_state(server_socket, result)) {
    close(server_socket);
    freeaddrinfo(result);
    return -1;
  }

  if (signal(SIGINT, &interrupt_signal) == SIG_ERR) {
    perror("Cannot set SIGINT handler");
    close(server_socket);
    destroy_proxy_state();
    return -1;
  }

  printf("Listen %d...\n", server_port);

  proxy_executor();

  destroy_proxy_state();
  return -1;
}

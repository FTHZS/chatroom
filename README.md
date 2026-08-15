# Chatroom (v11)

A LAN chatroom with a Swing GUI client, multithreaded server, file transfer, and emoji support — version 11 of an iterated project.

## About

A local-network chatroom: a multithreaded `ChatServer` accepts connections from multiple `ChatClient` instances, each running its own Swing GUI with message history, an emoji picker, and file upload. The server tracks connected users, broadcasts the live user list to everyone, supports kicking users, and handles file transfers as base64-encoded chat commands over that same connection — no separate protocol needed for a file versus a message.

This is version 11 of the project, and the codebase carries the marks of that iteration: a `codebin.txt` of snippets kept aside between versions, a dedicated `FileClient` split out from the main client, and a `Setup` class for first-run configuration. It also runs a small auto-update service on a separate port, serving a fresh `ChatClient.class` to anyone running an outdated client.

## How it works

- One thread per connected client (`ClientHandler extends Thread`), with the client set and message log kept as synchronized collections so broadcasts stay consistent across threads
- File uploads are just another chat command — `/file <name> <base64>` over the same socket — no separate transfer protocol needed
- A second listener thread accepts server-console commands directly (kick a user, shut down cleanly) without interrupting client handling
- On setup, the app can register itself as a desktop shortcut with a custom icon — a small first-run convenience most versions before v11 didn't have

## Tech

Java · Swing · multithreaded socket server · base64-encoded file transfer over the chat protocol

## Status

Working, v11 of an ongoing iterated project — see `codebin.txt` for snippets carried over from earlier versions.

---

*Built by [Abhinav Biju](https://abhinavbijuportfolio.onrender.com) — see the [full case study](https://abhinavbijuportfolio.onrender.com/projects/chatroom) for a live preview.*

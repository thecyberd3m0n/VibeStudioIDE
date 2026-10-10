# File Tool Guidelines

The `file` skill provides file system management tools within the VibeStudio Termux workspace environment.

## File Tool Best Practices
1. **Traverse Workspace**: Use `file_list_directory` to inspect directory structures or verify file presence before performing operations.
2. **File & Directory Management**:
   - Use `file_create_file` or `file_create_directory` to create new workspace resources.
   - Use `file_delete`, `file_move`, `file_copy` to organize files safely.
3. **Paths**: Absolute paths or paths relative to the Termux home workspace (`/data/data/com.termux/files/home`) are supported.
4. **Token Optimization**: Avoid listing huge directory structures recursively. Inspect specific target folders.
